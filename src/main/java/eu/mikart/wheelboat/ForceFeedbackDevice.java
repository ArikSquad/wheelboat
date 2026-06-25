package eu.mikart.wheelboat;

interface ForceFeedbackDevice extends AutoCloseable {
    void update(ForceFeedbackRequest request);

    @Override
    void close();
}
