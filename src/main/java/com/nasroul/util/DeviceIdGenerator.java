package com.nasroul.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * Generates a unique device ID for sync operations
 * Based on hostname and MAC address
 */
public class DeviceIdGenerator {

    private static String deviceId = null;

    /**
     * Get the current device ID (cached after first call)
     *
     * @return Unique device identifier
     */
    public static String getDeviceId() {
        if (deviceId == null) {
            deviceId = generateDeviceId();
        }
        return deviceId;
    }

    /**
     * Generate a device ID based on hostname and MAC address
     *
     * @return Generated device ID
     */
    private static String generateDeviceId() {
        StringBuilder id = new StringBuilder();

        // Try to get hostname
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            id.append(hostname);
        } catch (UnknownHostException e) {
            id.append("unknown-host");
        }

        // Try to get MAC address
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(localHost);

            if (network == null) {
                // Try to find first non-loopback interface
                Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
                while (networks.hasMoreElements()) {
                    NetworkInterface ni = networks.nextElement();
                    if (!ni.isLoopback() && ni.isUp() && ni.getHardwareAddress() != null) {
                        network = ni;
                        break;
                    }
                }
            }

            if (network != null) {
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    id.append("-");
                    StringBuilder macStr = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        macStr.append(String.format("%02X", mac[i]));
                    }
                    id.append(macStr.toString());
                }
            }
        } catch (Exception e) {
            // If we can't get MAC address, use system properties
            id.append("-").append(System.getProperty("user.name", "user"));
        }

        return id.toString();
    }

    /**
     * Force regeneration of device ID (for testing)
     */
    public static void reset() {
        deviceId = null;
    }
}
