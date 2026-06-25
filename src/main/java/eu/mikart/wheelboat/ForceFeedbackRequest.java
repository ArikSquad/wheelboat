package eu.mikart.wheelboat;

record ForceFeedbackRequest(boolean enabled, String device, float springStrength, float roughnessStrength) {
    ForceFeedbackRequest {
        device = device == null ? "" : device;
        springStrength = Math.clamp(springStrength, 0.0F, 1.0F);
        roughnessStrength = Math.clamp(roughnessStrength, 0.0F, 1.0F);
    }
}
