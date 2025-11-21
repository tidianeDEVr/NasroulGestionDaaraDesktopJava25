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
    private String lastErrorMessage = null;

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
        lastErrorMessage = null;
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
                    lastErrorMessage = "Impossible de lire le solde SMS.\n\n" +
                                      "La réponse du serveur SMS est mal formatée.\n" +
                                      "Veuillez contacter le support technique.";
                    System.err.println("Could not parse credits from XML: " + body);
                    return -1;
                } catch (Exception e) {
                    lastErrorMessage = "Erreur de lecture du solde SMS.\n\n" +
                                      "Le format de réponse du serveur est invalide.\n" +
                                      "Veuillez contacter le support technique.";
                    System.err.println("Error parsing balance response: " + body);
                    return -1;
                }
            } else {
                // Provide user-friendly error based on HTTP status
                if (response.getStatus() == 401 || response.getStatus() == 403) {
                    lastErrorMessage = "Erreur d'authentification SMS.\n\n" +
                                      "Vos identifiants SMS sont incorrects ou expirés.\n\n" +
                                      "Veuillez vérifier:\n" +
                                      "• L'ID de compte SMS\n" +
                                      "• Le mot de passe SMS\n" +
                                      "• Que votre compte SMS est actif";
                } else if (response.getStatus() >= 500) {
                    lastErrorMessage = "Serveur SMS indisponible.\n\n" +
                                      "Le serveur SMS rencontre des difficultés techniques.\n" +
                                      "Veuillez réessayer dans quelques minutes.";
                } else {
                    lastErrorMessage = "Impossible de vérifier le solde SMS.\n\n" +
                                      "Le serveur SMS a retourné une erreur.\n" +
                                      "Code d'erreur: " + response.getStatus() + "\n\n" +
                                      "Veuillez réessayer ou contacter le support.";
                }
                System.err.println("Error checking balance: " + response.getStatus() + " - " + response.getBody());
                return -1;
            }
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("timeout") ||
                e.getMessage().contains("timed out"))) {
                lastErrorMessage = "Délai d'attente dépassé.\n\n" +
                                  "La connexion au serveur SMS a pris trop de temps.\n\n" +
                                  "Veuillez vérifier:\n" +
                                  "• Votre connexion Internet\n" +
                                  "• Réessayer dans quelques instants";
            } else if (e.getMessage() != null && (e.getMessage().contains("UnknownHost") ||
                       e.getMessage().contains("connection"))) {
                lastErrorMessage = "Impossible de joindre le serveur SMS.\n\n" +
                                  "Veuillez vérifier:\n" +
                                  "• Votre connexion Internet\n" +
                                  "• Que l'URL du serveur SMS est correcte\n" +
                                  "• Votre pare-feu";
            } else {
                lastErrorMessage = "Erreur de connexion au serveur SMS.\n\n" +
                                  "Détails: " + e.getMessage() + "\n\n" +
                                  "Veuillez vérifier votre connexion Internet.";
            }
            System.err.println("Exception checking SMS balance: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get the last error message from SMS operations
     * @return the last error message, or null if no error
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
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
        lastErrorMessage = null;
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
                // Provide user-friendly error messages
                if (response.getStatus() == 401 || response.getStatus() == 403) {
                    lastErrorMessage = "Erreur d'authentification SMS.\n\n" +
                                      "Vos identifiants SMS sont incorrects.\n" +
                                      "Veuillez vérifier la configuration.";
                } else if (response.getStatus() == 400) {
                    lastErrorMessage = "Erreur d'envoi SMS.\n\n" +
                                      "Le numéro de téléphone ou le message est invalide.\n" +
                                      "Numéro: " + formattedPhone;
                } else if (response.getStatus() >= 500) {
                    lastErrorMessage = "Serveur SMS indisponible.\n\n" +
                                      "Le serveur SMS rencontre des difficultés.\n" +
                                      "Veuillez réessayer plus tard.";
                } else {
                    lastErrorMessage = "Erreur d'envoi SMS.\n\n" +
                                      "Code d'erreur: " + response.getStatus();
                }
                System.err.println("Error sending SMS to " + formattedPhone + ": " +
                    response.getStatus() + " - " + response.getBody());
                return false;
            }
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("timeout") ||
                e.getMessage().contains("timed out"))) {
                lastErrorMessage = "Délai d'attente dépassé.\n\n" +
                                  "L'envoi du SMS a pris trop de temps.\n" +
                                  "Veuillez réessayer.";
            } else if (e.getMessage() != null && (e.getMessage().contains("UnknownHost") ||
                       e.getMessage().contains("connection"))) {
                lastErrorMessage = "Impossible de joindre le serveur SMS.\n\n" +
                                  "Veuillez vérifier votre connexion Internet.";
            } else {
                lastErrorMessage = "Erreur d'envoi SMS.\n\n" +
                                  "Détails: " + e.getMessage();
            }
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
