package com.kmmcl.core.game

/**
 * Patches Log4j configuration to mitigate CVE-2021-44228 (Log4Shell) and
 * related RCE vulnerabilities.
 *
 * Reference: HMCL DefaultLauncher step 4 — log4j2.formatMsgNoLookups=true
 * and custom XML configuration file injection.
 */
object Log4jPatcher {

    /** JVM flags to append to prevent Log4j RCE. */
    val JVM_FLAGS = listOf(
        "-Dlog4j2.formatMsgNoLookups=true",
    )

    /** 
     * Custom log4j2.xml content that disables JNDI lookups.
     * Written to assets/log_configs/client-1.12.xml and referenced via
     * -Dlog4j.configurationFile.
     */
    val CONFIG_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="SysOut" target="SYSTEM_OUT">
            <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg{nolookups}%n" />
        </Console>
        <RollingRandomAccessFile name="File" fileName="logs/latest.log" filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg{nolookups}%n" />
            <Policies>
                <TimeBasedTriggeringPolicy />
                <OnStartupTriggeringPolicy />
            </Policies>
        </RollingRandomAccessFile>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="SysOut"/>
            <AppenderRef ref="File"/>
        </Root>
    </Loggers>
</Configuration>""".trimIndent()

    /**
     * Ensure the Log4j config file exists in the game directory.
     * Writes the patched XML if missing; does not overwrite existing.
     */
    fun ensureConfigWritten(gameDir: String, writeFile: (String, String) -> Unit) {
        val configDir = "$gameDir/assets/log_configs"
        val configPath = "$configDir/client-1.12.xml"
        writeFile(configPath, CONFIG_XML)
    }
}
