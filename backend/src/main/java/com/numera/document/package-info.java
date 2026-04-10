@org.springframework.modulith.ApplicationModule(
        displayName = "document",
        allowedDependencies = {
                "auth::domain",
                "auth::infrastructure",
                "customer::domain",
                "customer::infrastructure",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::exception",
                "shared::security"
        }
)
package com.numera.document;
