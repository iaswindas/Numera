/**
 * Named interface exposing the model module's application-layer services (FormulaEngine,
 * TemplateService) so that the spreading and covenant modules can depend on them without having
 * to access model's private sub-package directly.
 */
@org.springframework.modulith.NamedInterface("application")
package com.numera.model.application;
