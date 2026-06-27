package com.sanguosha.assistant.config;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LanStartupPrinter {
    @Value("${server.port}")
    private int port;

    @EventListener(ApplicationReadyEvent.class)
    public void printLanUrls() {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("SGS LAN 已启动");
        System.out.println("本机访问: http://localhost:" + port);
        lanAddresses().forEach(address -> System.out.println("手机访问: http://" + address + ":" + port));
        System.out.println("手机和电脑需要连接同一个 WiFi 或电脑热点。");
        System.out.println("==================================================");
        System.out.println();
    }

    private List<String> lanAddresses() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(networkInterface -> {
                        try {
                            return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(Inet4Address.class::isInstance)
                    .map(address -> address.getHostAddress())
                    .filter(address -> address.startsWith("192.168.")
                            || address.startsWith("10.")
                            || address.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
