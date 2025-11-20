package com.nasroul.util;

import com.nasroul.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtil {

    public static void generateMemberTemplate(File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Members");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"First Name*", "Last Name*", "Email", "Phone", "Birth Date (YYYY-MM-DD)",
                              "Address", "Join Date (YYYY-MM-DD)*", "Role", "Active (true/false)",
                              "Group IDs (comma-separated)"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    public static void generateEventTemplate(File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Events");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Name*", "Description", "Start Date (YYYY-MM-DDTHH:MM:SS)*",
                              "End Date (YYYY-MM-DDTHH:MM:SS)", "Location", "Status", "Organizer ID",
                              "Max Capacity", "Active (true/false)", "Contribution Target"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    public static void generateProjectTemplate(File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Projects");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Name*", "Description", "Start Date (YYYY-MM-DD)",
                              "End Date (YYYY-MM-DD)", "Status", "Budget", "Target Budget",
                              "Manager ID", "Contribution Target"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    public static void generateExpenseTemplate(File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Expenses");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Description*", "Amount*", "Date (YYYY-MM-DD)*",
                              "Category", "Entity Type (EVENT/PROJECT)*", "Entity ID*", "Member ID"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    public static List<Member> importMembers(File file) throws IOException {
        List<Member> members = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Member member = new Member();
                member.setFirstName(getCellValueAsString(row.getCell(0)));
                member.setLastName(getCellValueAsString(row.getCell(1)));
                member.setEmail(getCellValueAsString(row.getCell(2)));
                member.setPhone(getCellValueAsString(row.getCell(3)));

                String birthDate = getCellValueAsString(row.getCell(4));
                if (birthDate != null && !birthDate.isEmpty()) {
                    member.setBirthDate(LocalDate.parse(birthDate));
                }

                member.setAddress(getCellValueAsString(row.getCell(5)));

                String joinDate = getCellValueAsString(row.getCell(6));
                if (joinDate != null && !joinDate.isEmpty()) {
                    member.setJoinDate(LocalDate.parse(joinDate));
                }

                member.setRole(getCellValueAsString(row.getCell(7)));

                String active = getCellValueAsString(row.getCell(8));
                member.setActive(active == null || !active.equalsIgnoreCase("false"));

                // Group IDs (comma-separated)
                String groupIds = getCellValueAsString(row.getCell(9));
                if (groupIds != null && !groupIds.isEmpty()) {
                    List<Integer> groupIdList = new ArrayList<>();
                    for (String id : groupIds.split(",")) {
                        id = id.trim();
                        if (!id.isEmpty()) {
                            groupIdList.add(Integer.parseInt(id));
                        }
                    }
                    member.setGroupIds(groupIdList);
                }

                members.add(member);
            }
        }

        return members;
    }

    public static List<Event> importEvents(File file) throws IOException {
        List<Event> events = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Event event = new Event();
                event.setName(getCellValueAsString(row.getCell(0)));
                event.setDescription(getCellValueAsString(row.getCell(1)));

                String startDate = getCellValueAsString(row.getCell(2));
                if (startDate != null && !startDate.isEmpty()) {
                    event.setStartDate(LocalDateTime.parse(startDate));
                }

                String endDate = getCellValueAsString(row.getCell(3));
                if (endDate != null && !endDate.isEmpty()) {
                    event.setEndDate(LocalDateTime.parse(endDate));
                }

                event.setLocation(getCellValueAsString(row.getCell(4)));
                event.setStatus(getCellValueAsString(row.getCell(5)));

                String organizerId = getCellValueAsString(row.getCell(6));
                if (organizerId != null && !organizerId.isEmpty()) {
                    event.setOrganizerId(Integer.parseInt(organizerId));
                }

                String maxCapacity = getCellValueAsString(row.getCell(7));
                if (maxCapacity != null && !maxCapacity.isEmpty()) {
                    event.setMaxCapacity(Integer.parseInt(maxCapacity));
                }

                String active = getCellValueAsString(row.getCell(8));
                event.setActive(active == null || !active.equalsIgnoreCase("false"));

                String contributionTarget = getCellValueAsString(row.getCell(9));
                if (contributionTarget != null && !contributionTarget.isEmpty()) {
                    event.setContributionTarget(Double.parseDouble(contributionTarget));
                }

                events.add(event);
            }
        }

        return events;
    }

    public static List<Project> importProjects(File file) throws IOException {
        List<Project> projects = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Project project = new Project();
                project.setName(getCellValueAsString(row.getCell(0)));
                project.setDescription(getCellValueAsString(row.getCell(1)));

                String startDate = getCellValueAsString(row.getCell(2));
                if (startDate != null && !startDate.isEmpty()) {
                    project.setStartDate(LocalDate.parse(startDate));
                }

                String endDate = getCellValueAsString(row.getCell(3));
                if (endDate != null && !endDate.isEmpty()) {
                    project.setEndDate(LocalDate.parse(endDate));
                }

                project.setStatus(getCellValueAsString(row.getCell(4)));

                String budget = getCellValueAsString(row.getCell(5));
                if (budget != null && !budget.isEmpty()) {
                    project.setBudget(Double.parseDouble(budget));
                }

                String targetBudget = getCellValueAsString(row.getCell(6));
                if (targetBudget != null && !targetBudget.isEmpty()) {
                    project.setTargetBudget(Double.parseDouble(targetBudget));
                }

                String managerId = getCellValueAsString(row.getCell(7));
                if (managerId != null && !managerId.isEmpty()) {
                    project.setManagerId(Integer.parseInt(managerId));
                }

                String contributionTarget = getCellValueAsString(row.getCell(8));
                if (contributionTarget != null && !contributionTarget.isEmpty()) {
                    project.setContributionTarget(Double.parseDouble(contributionTarget));
                }

                projects.add(project);
            }
        }

        return projects;
    }

    public static List<Expense> importExpenses(File file) throws IOException {
        List<Expense> expenses = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Expense expense = new Expense();
                expense.setDescription(getCellValueAsString(row.getCell(0)));

                String amount = getCellValueAsString(row.getCell(1));
                if (amount != null && !amount.isEmpty()) {
                    expense.setAmount(Double.parseDouble(amount));
                }

                String date = getCellValueAsString(row.getCell(2));
                if (date != null && !date.isEmpty()) {
                    expense.setDate(LocalDate.parse(date));
                }

                expense.setCategory(getCellValueAsString(row.getCell(3)));
                expense.setEntityType(getCellValueAsString(row.getCell(4)));

                String entityId = getCellValueAsString(row.getCell(5));
                if (entityId != null && !entityId.isEmpty()) {
                    expense.setEntityId(Integer.parseInt(entityId));
                }

                String memberId = getCellValueAsString(row.getCell(6));
                if (memberId != null && !memberId.isEmpty()) {
                    expense.setMemberId(Integer.parseInt(memberId));
                }

                expenses.add(expense);
            }
        }

        return expenses;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
