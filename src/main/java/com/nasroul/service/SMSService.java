package com.nasroul.service;

import com.nasroul.util.ConfigManager;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

public class SMSService {
    private final ConfigManager config;
    private final String ACCOUNT_ID;
    private final String PASSWORD;
    private final String SENDER;
    private final String API_URL;
    private final String CREDITS_URL;

    public SMSService() {
        this.config = ConfigManager.getInstance();
        this.ACCOUNT_ID = config.getSmsAccountId();
        this.PASSWORD = config.getSmsPassword();
        this.SENDER = config.getSmsSender();
        this.API_URL = config.getSmsApiUrl();
        this.CREDITS_URL = config.getSmsCreditsUrl();
    }

    /**
     * Check the SMS balance available
     * @return the number of SMS credits available, or -1 if error
     */
    public int checkSMSBalance() {
        try {
            HttpResponse<String> response = Unirest.get(CREDITS_URL)
                .queryString("accountid", ACCOUNT_ID)
                .queryString("password", PASSWORD)
                .asString();

            if (response.isSuccess()) {
                // Parse XML response
                // Format: <credits><route><type>...</type><credits>343</credits>...
                String body = response.getBody();
                try {
                    // Simple XML parsing to extract first <credits> tag value
                    int creditsStart = body.indexOf("<credits>", body.indexOf("<route>"));
                    if (creditsStart != -1) {
                        creditsStart += "<credits>".length();
                        int creditsEnd = body.indexOf("</credits>", creditsStart);
                        if (creditsEnd != -1) {
                            String creditsValue = body.substring(creditsStart, creditsEnd).trim();
                            return Integer.parseInt(creditsValue);
                        }
                    }
                    System.err.println("Could not parse credits from XML: " + body);
                    return -1;
                } catch (Exception e) {
                    System.err.println("Error parsing balance response: " + body);
                    return -1;
                }
            } else {
                System.err.println("Error checking balance: " + response.getStatus() + " - " + response.getBody());
                return -1;
            }
        } catch (Exception e) {
            System.err.println("Exception checking SMS balance: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Format phone number to international format (+221...)
     * @param phoneNumber the phone number to format
     * @return formatted phone number
     */
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }

        // Remove all spaces and special characters
        String cleaned = phoneNumber.replaceAll("[\\s\\-\\(\\)\\.]", "");

        // If already starts with +221, return as is
        if (cleaned.startsWith("+221")) {
            return cleaned;
        }

        // If starts with 221, add +
        if (cleaned.startsWith("221")) {
            return "+" + cleaned;
        }

        // If starts with 00221, replace with +221
        if (cleaned.startsWith("00221")) {
            return "+" + cleaned.substring(2);
        }

        // If starts with 7 (local Senegal number), add +221
        if (cleaned.startsWith("7") && cleaned.length() == 9) {
            return "+221" + cleaned;
        }

        // Default: assume it's a local number and add +221
        return "+221" + cleaned;
    }

    /**
     * Send an SMS to a single recipient
     * @param phoneNumber the recipient's phone number
     * @param message the message to send
     * @return true if sent successfully, false otherwise
     */
    public boolean sendSMS(String phoneNumber, String message) {
        try {
            // Format phone number
            String formattedPhone = formatPhoneNumber(phoneNumber);

            JSONObject requestBody = new JSONObject();
            requestBody.put("accountid", ACCOUNT_ID);
            requestBody.put("password", PASSWORD);
            requestBody.put("sender", SENDER);
            requestBody.put("text", message);
            requestBody.put("to", formattedPhone);

            HttpResponse<String> response = Unirest.post(API_URL)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .asString();

            if (response.isSuccess()) {
                System.out.println("SMS sent successfully to " + formattedPhone);
                return true;
            } else {
                System.err.println("Error sending SMS to " + formattedPhone + ": " +
                    response.getStatus() + " - " + response.getBody());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Exception sending SMS to " + phoneNumber + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Replace template variables in the message
     * @param template the message template with variables
     * @param firstName member's first name
     * @param lastName member's last name
     * @param remainingAmount the remaining amount to pay
     * @param totalAmount the total amount expected for the event/project
     * @param entityName the name of the event or project
     * @return the message with variables replaced
     */
    public String replaceVariables(String template, String firstName, String lastName,
                                   double remainingAmount, double totalAmount, String entityName) {
        String message = template;
        message = message.replace("{prenom}", firstName != null ? firstName : "");
        message = message.replace("{nom}", lastName != null ? lastName : "");
        message = message.replace("{montant_restant}", String.format("%.0f", remainingAmount));
        message = message.replace("{montant_total}", String.format("%.0f", totalAmount));
        message = message.replace("{nom_evenement}", entityName != null ? entityName : "");
        message = message.replace("{nom_projet}", entityName != null ? entityName : "");
        return message;
    }

    /**
     * Get the list of available variables for SMS templates
     * @return a formatted string listing available variables
     */
    public String getAvailableVariables() {
        return "Variables disponibles : {nom}, {prenom}, {montant_restant}, {montant_total}, {nom_evenement}, {nom_projet}";
    }
}
