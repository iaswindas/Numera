@org.springframework.modulith.ApplicationModule(
        displayName = "spreading",
        allowedDependencies = {
                "customer::domain",
                "customer::infrastructure",
                "document::domain",
                "document::infrastructure",
                "model::application",
                "model::domain",
                "model::dto",
                "model::infrastructure",
                "shared::audit",
                "shared::domain",
                "shared::exception"
        }
)
package com.numera.spreading;
