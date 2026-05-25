package com.appscreener.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.backup")
public class BackupProperties {

    /** Включить архивацию истории отчётов */
    private boolean enabled = true;

    /** Каталог архивов (history-*.json.gz) */
    private String directory = "./backups";

    /** Расписание: по умолчанию раз в неделю (воскресенье 03:00) */
    private String cron = "0 0 3 * * SUN";

    /** Сколько последних архивов хранить */
    private int keepCount = 8;

    /** Не создавать архив при старте приложения */
    private boolean onStartup = false;

    /** Пропускать плановый архив, если число прогонов/сообщений и дата активности не изменились */
    private boolean skipIfUnchanged = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getKeepCount() {
        return keepCount;
    }

    public void setKeepCount(int keepCount) {
        this.keepCount = keepCount;
    }

    public boolean isOnStartup() {
        return onStartup;
    }

    public void setOnStartup(boolean onStartup) {
        this.onStartup = onStartup;
    }

    public boolean isSkipIfUnchanged() {
        return skipIfUnchanged;
    }

    public void setSkipIfUnchanged(boolean skipIfUnchanged) {
        this.skipIfUnchanged = skipIfUnchanged;
    }
}
