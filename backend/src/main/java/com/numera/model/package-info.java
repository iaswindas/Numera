@org.springframework.modulith.ApplicationModule(
        displayName = "model",
        allowedDependencies = {
                "shared::audit",
                "shared::domain",
                "shared::exception"
        }
)
package com.numera.model;
