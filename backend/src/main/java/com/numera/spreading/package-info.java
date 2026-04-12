@org.springframework.modulith.ApplicationModule(
        displayName = "spreading",
        allowedDependencies = {
                "customer",
                "customer::domain",
                "customer::infrastructure",
                "document",
                "document::domain",
                "document::infrastructure",
                "model",
                "model::application",
                "model::domain",
                "model::dto",
                "model::infrastructure",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::events",
                "shared::exception",
                "shared::infrastructure",
                "shared::notification",
                "shared::security"
        }
)
package com.numera.spreading;
