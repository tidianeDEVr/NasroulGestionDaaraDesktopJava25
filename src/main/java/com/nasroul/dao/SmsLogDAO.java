package com.nasroul.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Journal local des envois SMS (table sms_log, jamais synchronisée).
 *
 * Chaque destinataire d'une campagne y est inscrit en PENDING avant l'envoi,
 * puis passe en SENT ou FAILED. La contrainte UNIQUE(campaign_id, phone)
 * garantit qu'une reprise de campagne ne peut pas renvoyer deux fois au même
 * numéro.
 */
public class SmsLogDAO {
    private final DatabaseManager dbManager;

    public SmsLogDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void insertPending(String campaignId, String entityType, Integer entityId, Integer groupId,
                              Integer memberId, String phone, String message, int segments) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO sms_log
                (campaign_id, entity_type, entity_id, group_id, member_id, phone, message, segments, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'))
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, campaignId);
            pstmt.setString(2, entityType);
            pstmt.setObject(3, entityId);
            pstmt.setObject(4, groupId);
            pstmt.setObject(5, memberId);
            pstmt.setString(6, phone);
            pstmt.setString(7, message);
            pstmt.setInt(8, segments);
            pstmt.executeUpdate();
        }
    }

    public void markSent(String campaignId, String phone, String providerResponse) throws SQLException {
        String sql = """
            UPDATE sms_log SET status = 'SENT', provider_response = ?, sent_at = datetime('now')
            WHERE campaign_id = ? AND phone = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, providerResponse);
            pstmt.setString(2, campaignId);
            pstmt.setString(3, phone);
            pstmt.executeUpdate();
        }
    }

    public void markFailed(String campaignId, String phone, String errorMessage) throws SQLException {
        String sql = """
            UPDATE sms_log SET status = 'FAILED', error_message = ?
            WHERE campaign_id = ? AND phone = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, errorMessage);
            pstmt.setString(2, campaignId);
            pstmt.setString(3, phone);
            pstmt.executeUpdate();
        }
    }

    /** Un destinataire à journaliser avant l'envoi. */
    public record PendingEntry(Integer memberId, String phone, String message, int segments) {
    }

    /** Journalise tous les destinataires en une seule transaction. */
    public void insertPendingBatch(String campaignId, String entityType, Integer entityId, Integer groupId,
                                   java.util.List<PendingEntry> entries) throws SQLException {
        if (entries.isEmpty()) {
            return;
        }
        String sql = """
            INSERT OR IGNORE INTO sms_log
                (campaign_id, entity_type, entity_id, group_id, member_id, phone, message, segments, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'))
            """;

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (PendingEntry entry : entries) {
                    pstmt.setString(1, campaignId);
                    pstmt.setString(2, entityType);
                    pstmt.setObject(3, entityId);
                    pstmt.setObject(4, groupId);
                    pstmt.setObject(5, entry.memberId());
                    pstmt.setString(6, entry.phone());
                    pstmt.setString(7, entry.message());
                    pstmt.setInt(8, entry.segments());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Une campagne agrégée (pour l'historique de la fiche Collecte). */
    public record CampaignSummary(String campaignId, String createdAt, Integer groupId,
                                  int recipients, int sent, int failed) {
    }

    /** Campagnes d'une entité, les plus récentes d'abord. */
    public java.util.List<CampaignSummary> findByEntity(String entityType, int entityId) throws SQLException {
        String sql = """
            SELECT campaign_id, MIN(created_at) AS created_at, MAX(group_id) AS group_id,
                   COUNT(*) AS recipients,
                   SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed
            FROM sms_log
            WHERE entity_type = ? AND entity_id = ?
            GROUP BY campaign_id
            ORDER BY MIN(created_at) DESC
            """;

        java.util.List<CampaignSummary> campaigns = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    campaigns.add(new CampaignSummary(
                        rs.getString("campaign_id"),
                        rs.getString("created_at"),
                        (Integer) rs.getObject("group_id"),
                        rs.getInt("recipients"),
                        rs.getInt("sent"),
                        rs.getInt("failed")));
                }
            }
        }
        return campaigns;
    }

    /** Numéros déjà servis dans une campagne : à sauter lors d'une reprise. */
    public Set<String> findSentPhones(String campaignId) throws SQLException {
        String sql = "SELECT phone FROM sms_log WHERE campaign_id = ? AND status = 'SENT'";
        Set<String> phones = new HashSet<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, campaignId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    phones.add(rs.getString("phone"));
                }
            }
        }
        return phones;
    }
}
