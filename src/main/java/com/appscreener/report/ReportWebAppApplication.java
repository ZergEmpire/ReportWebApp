package com.appscreener.report;

import com.appscreener.report.config.AllureTestOpsProperties;
import com.appscreener.report.config.AuthProperties;
import com.appscreener.report.config.BackupProperties;
import com.appscreener.report.config.DisplayTimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        BackupProperties.class,
        AllureTestOpsProperties.class,
        AuthProperties.class,
        DisplayTimeProperties.class
})
public class ReportWebAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportWebAppApplication.class, args);
    }
}
