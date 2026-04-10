@org.springframework.modulith.ApplicationModule(
        displayName = "auth",
        allowedDependencies = {
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::exception",
                "shared::infrastructure",
                "shared::security"
        }
)
package com.numera.auth;
