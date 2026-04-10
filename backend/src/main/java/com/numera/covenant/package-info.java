@org.springframework.modulith.ApplicationModule(
        displayName = "covenant",
        allowedDependencies = {
                "customer::domain",
                "customer::infrastructure",
                "document::infrastructure",
                "shared::audit",
                "shared::domain",
                "shared::exception"
        }
)
package com.numera.covenant;
