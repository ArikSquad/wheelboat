package eu.mikart.wheelboat;

final class MaterialForceFeedback {
    float springMultiplier;
    float roughness;

    MaterialForceFeedback(float springMultiplier, float roughness) {
        this.springMultiplier = springMultiplier;
        this.roughness = roughness;
    }

    void sanitize() {
        springMultiplier = Math.clamp(springMultiplier, 0.0F, 2.0F);
        roughness = Math.clamp(roughness, 0.0F, 1.0F);
    }
}
