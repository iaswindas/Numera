@org.springframework.modulith.ApplicationModule(
        displayName = "admin",
        allowedDependencies = {
                "auth",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::exception",
                "shared::security"
        }
)
package com.numera.admin;
