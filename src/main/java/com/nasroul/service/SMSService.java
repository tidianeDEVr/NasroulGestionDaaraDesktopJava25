package com.nasroul.service;

import com.nasroul.util.ConfigManager;
import com.nasroul.util.PhoneNumberValidator;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import java.util.Optional;

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
     * Result of one SMS send attempt: whether it succeeded, the raw provider
     * response body (journalized in sms_log), and a user-readable error.
     */
    public record SendResult(boolean success, String providerResponse, String errorMessage) {
    }

    /**
     * Send an SMS and return the detailed result, including the provider
     * response body. HTTP 200 alone is not trusted: the body is inspected for
     * application-level error markers.
     */
    public SendResult sendSMSDetailed(String phoneNumber, String message) {
        lastErrorMessage = null;
        try {
            Optional<String> normalized = PhoneNumberValidator.normalize(phoneNumber);
            if (normalized.isEmpty()) {
                String error = "Numéro de téléphone invalide : " + phoneNumber +
                               "\nFormat attendu : numéro mobile sénégalais (70/71/75/76/77/78 + 7 chiffres).";
                lastErrorMessage = error;
                return new SendResult(false, null, error);
            }
            String formattedPhone = normalized.get();

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

            String body = response.getBody();

            if (response.isSuccess() && !bodyIndicatesError(body)) {
                System.out.println("SMS sent successfully to " + formattedPhone);
                return new SendResult(true, body, null);
            }

            String error;
            if (response.isSuccess()) {
                // HTTP 200 mais erreur applicative dans le corps de la réponse
                error = "Le serveur SMS a refusé le message.\n\nRéponse: " + body;
            } else if (response.getStatus() == 401 || response.getStatus() == 403) {
                error = "Erreur d'authentification SMS.\n\n" +
                        "Vos identifiants SMS sont incorrects.\n" +
                        "Veuillez vérifier la configuration.";
            } else if (response.getStatus() == 400) {
                error = "Erreur d'envoi SMS.\n\n" +
                        "Le numéro de téléphone ou le message est invalide.\n" +
                        "Numéro: " + formattedPhone;
            } else if (response.getStatus() >= 500) {
                error = "Serveur SMS indisponible.\n\n" +
                        "Le serveur SMS rencontre des difficultés.\n" +
                        "Veuillez réessayer plus tard.";
            } else {
                error = "Erreur d'envoi SMS.\n\n" +
                        "Code d'erreur: " + response.getStatus();
            }
            lastErrorMessage = error;
            System.err.println("Error sending SMS to " + formattedPhone + ": " +
                response.getStatus() + " - " + body);
            return new SendResult(false, body, error);
        } catch (Exception e) {
            String error;
            if (e.getMessage() != null && (e.getMessage().contains("timeout") ||
                e.getMessage().contains("timed out"))) {
                error = "Délai d'attente dépassé.\n\n" +
                        "L'envoi du SMS a pris trop de temps.\n" +
                        "Veuillez réessayer.";
            } else if (e.getMessage() != null && (e.getMessage().contains("UnknownHost") ||
                       e.getMessage().contains("connection"))) {
                error = "Impossible de joindre le serveur SMS.\n\n" +
                        "Veuillez vérifier votre connexion Internet.";
            } else {
                error = "Erreur d'envoi SMS.\n\n" +
                        "Détails: " + e.getMessage();
            }
            lastErrorMessage = error;
            System.err.println("Exception sending SMS to " + phoneNumber + ": " + e.getMessage());
            e.printStackTrace();
            return new SendResult(false, null, error);
        }
    }

    /**
     * Detect application-level errors hidden behind an HTTP 200 (invalid
     * number, insufficient credits...). The raw body is always journalized,
     * so a false negative here still leaves a trace.
     */
    private boolean bodyIndicatesError(String body) {
        if (body == null) {
            return false;
        }
        // ATTENTION au biais : un faux échec provoque un renvoi (double SMS,
        // double coût) alors qu'un faux succès est simplement journalisé avec
        // le corps brut dans sms_log. On ne signale donc un échec que sur des
        // marqueurs FORTS et non ambigus — jamais sur un simple mot du texte
        // du message (un rappel en français peut contenir « erreur »...).
        String lower = body.toLowerCase();
        return lower.contains("\"status\":\"error\"")
                || lower.contains("\"success\":false")
                || lower.contains("insufficient credit")
                || lower.contains("credit insuffisant")
                || lower.contains("invalid number")
                || lower.contains("invalid recipient")
                || lower.contains("unauthorized");
    }

    /**
     * Replace template variables in the message
     */
    public String replaceVariables(String template, String firstName, String lastName,
                                   double remainingAmount, double totalAmount, String entityName,
                                   double amountPaid, double globalRemaining, String groupName, String deadline) {
        String message = template;
        message = message.replace("{prenom}", firstName != null ? firstName : "");
        message = message.replace("{nom}", lastName != null ? lastName : "");
        message = message.replace("{montant_restant}", String.format("%.0f", remainingAmount));
        message = message.replace("{montant_total}", String.format("%.0f", totalAmount));
        message = message.replace("{nom_evenement}", entityName != null ? entityName : "");
        message = message.replace("{nom_projet}", entityName != null ? entityName : "");
        message = message.replace("{montant_paye}", String.format("%.0f", amountPaid));
        message = message.replace("{montant_cible_restant}", String.format("%.0f", globalRemaining));
        message = message.replace("{nom_groupe}", groupName != null ? groupName : "");
        message = message.replace("{date_echeance}", deadline != null ? deadline : "");
        return message;
    }

}
