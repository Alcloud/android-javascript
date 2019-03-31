package eu.credential.app.patient.orchestration.collection;

public interface WithCollectorService {

    void setCollectorService(CollectorService collectorService);

    void refreshMessages();

    void refreshMeasurements();

    void listMessage(final String message);

    void displayConnectionStateChange(String deviceAddress, String deviceName, boolean hasConnected);
}
