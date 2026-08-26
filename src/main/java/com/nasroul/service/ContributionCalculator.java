package com.nasroul.service;

import com.nasroul.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Single source of truth for every contribution amount shown in the app
 * (dashboard, views, SMS reminders).
 *
 * Rules:
 * - target  = the unique payment_groups row for (group, entity); "no target defined"
 *             is distinct from a target of 0
 * - paid    = SUM of the member's PAID contributions for the entity, restricted to
 *             the group (legacy rows with group_id NULL count for the member too:
 *             a payment is a payment)
 * - remaining = max(0, target - paid); an overpayment is reported as surplus,
 *             never folded back into "paid"
 * - expected  = target × active member count of the group (via member_groups)
 */
public class ContributionCalculator {

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection get() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    public ContributionCalculator() {
        this(() -> DatabaseManager.getInstance().getConnection());
    }

    public ContributionCalculator(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Financial status of one member for one (entity, group).
     *
     * @param targetPerMember amount expected from each member, or null if no
     *                        payment target is defined for this group and entity
     * @param paid            sum of the member's PAID contributions
     * @param remaining       what the member still owes (0 when no target defined)
     * @param surplus         how much the member paid beyond the target
     */
    public record MemberContributionStatus(
            Double targetPerMember,
            double paid,
            double remaining,
            double surplus) {

        public boolean targetDefined() {
            return targetPerMember != null;
        }
    }

    public MemberContributionStatus forMember(int memberId, String entityType, int entityId, int groupId)
            throws SQLException {
        Double target = findTarget(entityType, entityId, groupId);
        double paid = paidByMember(memberId, entityType, entityId, groupId);

        if (target == null) {
            return new MemberContributionStatus(null, paid, 0.0, 0.0);
        }
        return new MemberContributionStatus(
                target,
                paid,
                Math.max(0.0, target - paid),
                Math.max(0.0, paid - target));
    }

    /**
     * The per-member target for (entity, group), or null if none is defined.
     * The partial unique index guarantees at most one active row.
     */
    public Double findTarget(String entityType, int entityId, int groupId) throws SQLException {
        String sql = """
            SELECT amount FROM payment_groups
            WHERE entity_type = ? AND entity_id = ? AND group_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            pstmt.setInt(3, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("amount");
                }
            }
        }
        return null;
    }

    /**
     * Sum of the member's PAID contributions for the entity. Contributions
     * attached to another group are excluded; legacy rows (group_id NULL)
     * still count for the member.
     */
    public double paidByMember(int memberId, String entityType, int entityId, int groupId) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) FROM contributions
            WHERE member_id = ? AND entity_type = ? AND entity_id = ?
              AND status = 'PAID' AND deleted_at IS NULL
              AND (group_id = ? OR group_id IS NULL)
            """;

        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, memberId);
            pstmt.setString(2, entityType);
            pstmt.setInt(3, entityId);
            pstmt.setInt(4, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    /**
     * Amount expected from one group for one entity: target × active members.
     * Returns 0 when no target is defined.
     */
    public double expectedForGroup(String entityType, int entityId, int groupId) throws SQLException {
        Double target = findTarget(entityType, entityId, groupId);
        if (target == null) {
            return 0.0;
        }
        return target * countActiveMembers(groupId);
    }

    /**
     * Total expected for an entity, summed over every group having a target.
     * This is the derived value that replaces the old hand-typed
     * events/projects.contribution_target.
     */
    public double expectedForEntity(String entityType, int entityId) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(pg.amount * (
                       SELECT COUNT(*) FROM member_groups mg
                       JOIN members m ON m.id = mg.member_id
                            AND m.deleted_at IS NULL AND m.active = 1
                       WHERE mg.group_id = pg.group_id)), 0)
            FROM payment_groups pg
            WHERE pg.entity_type = ? AND pg.entity_id = ? AND pg.deleted_at IS NULL
            """;

        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    /**
     * Grand total expected across all live entities (dashboard).
     * Targets pointing to a soft-deleted event/project are excluded, so this
     * stays consistent with the paid totals which also ignore deleted entities.
     */
    public double totalExpectedAll() throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(pg.amount * (
                       SELECT COUNT(*) FROM member_groups mg
                       JOIN members m ON m.id = mg.member_id
                            AND m.deleted_at IS NULL AND m.active = 1
                       WHERE mg.group_id = pg.group_id)), 0)
            FROM payment_groups pg
            LEFT JOIN events e ON pg.entity_type = 'EVENT' AND e.id = pg.entity_id
            LEFT JOIN projects p ON pg.entity_type = 'PROJECT' AND p.id = pg.entity_id
            WHERE pg.deleted_at IS NULL
              AND ((pg.entity_type = 'EVENT' AND e.id IS NOT NULL AND e.deleted_at IS NULL)
                OR (pg.entity_type = 'PROJECT' AND p.id IS NOT NULL AND p.deleted_at IS NULL))
            """;

        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    /**
     * Montant attendu de CHAQUE entité en une seule requête (clé "TYPE:id").
     * Évite le N+1 des listes (dashboard, Collectes).
     */
    public java.util.Map<String, Double> expectedTotalsByEntity() throws SQLException {
        String sql = """
            SELECT pg.entity_type, pg.entity_id,
                   SUM(pg.amount * (
                       SELECT COUNT(*) FROM member_groups mg
                       JOIN members m ON m.id = mg.member_id
                            AND m.deleted_at IS NULL AND m.active = 1
                       WHERE mg.group_id = pg.group_id)) AS expected
            FROM payment_groups pg
            WHERE pg.deleted_at IS NULL
            GROUP BY pg.entity_type, pg.entity_id
            """;

        java.util.Map<String, Double> totals = new java.util.HashMap<>();
        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                totals.put(rs.getString("entity_type") + ":" + rs.getInt("entity_id"),
                    rs.getDouble("expected"));
            }
        }
        return totals;
    }

    /**
     * Ligne de la vue de recouvrement : un membre du groupe avec sa cible,
     * son payé et son restant — mêmes règles que {@link #forMember}.
     */
    public record MemberRecoveryRow(
            int memberId,
            String fullName,
            String phone,
            Double targetPerMember,
            double paid,
            double remaining,
            double surplus) {

        public boolean targetDefined() {
            return targetPerMember != null;
        }

        public boolean isLate() {
            return targetDefined() && remaining > 0;
        }
    }

    /**
     * Tous les membres actifs du groupe avec leur situation pour l'entité,
     * en UNE requête agrégée (pas de N+1). Les cotisations legacy sans
     * group_id comptent pour le membre, comme dans {@link #paidByMember}.
     */
    public java.util.List<MemberRecoveryRow> recoveryForGroup(String entityType, int entityId, int groupId)
            throws SQLException {
        String sql = """
            SELECT m.id, m.first_name, m.last_name, m.phone, pg.amount AS target,
                   COALESCE(SUM(CASE WHEN c.status = 'PAID' THEN c.amount END), 0) AS paid
            FROM member_groups mg
            JOIN members m ON m.id = mg.member_id AND m.deleted_at IS NULL AND m.active = 1
            LEFT JOIN payment_groups pg ON pg.entity_type = ? AND pg.entity_id = ?
                 AND pg.group_id = mg.group_id AND pg.deleted_at IS NULL
            LEFT JOIN contributions c ON c.member_id = m.id
                 AND c.entity_type = ? AND c.entity_id = ?
                 AND c.deleted_at IS NULL
                 AND (c.group_id = mg.group_id OR c.group_id IS NULL)
            WHERE mg.group_id = ?
            GROUP BY m.id, m.first_name, m.last_name, m.phone, pg.amount
            ORDER BY m.last_name, m.first_name
            """;

        java.util.List<MemberRecoveryRow> rows = new java.util.ArrayList<>();
        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            pstmt.setString(3, entityType);
            pstmt.setInt(4, entityId);
            pstmt.setInt(5, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    double targetValue = rs.getDouble("target");
                    Double target = rs.wasNull() ? null : targetValue;
                    double paid = rs.getDouble("paid");
                    double remaining = target != null ? Math.max(0.0, target - paid) : 0.0;
                    double surplus = target != null ? Math.max(0.0, paid - target) : 0.0;
                    rows.add(new MemberRecoveryRow(
                        rs.getInt("id"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("phone"),
                        target, paid, remaining, surplus));
                }
            }
        }
        return rows;
    }

    private int countActiveMembers(int groupId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM member_groups mg
            JOIN members m ON m.id = mg.member_id
                 AND m.deleted_at IS NULL AND m.active = 1
            WHERE mg.group_id = ?
            """;

        try (Connection conn = connectionProvider.get();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
