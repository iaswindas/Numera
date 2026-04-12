@org.springframework.modulith.ApplicationModule(
        displayName = "model",
        allowedDependencies = {
                "shared::audit",
                "shared::domain",
                "shared::exception",
                "shared::security"
        }
)
package com.numera.model;
