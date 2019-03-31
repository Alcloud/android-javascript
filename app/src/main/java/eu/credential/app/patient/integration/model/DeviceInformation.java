package eu.credential.app.patient.integration.model;

/**
 * Representation of the 0x180a "Device Information" gatt service.
 */
public class DeviceInformation {

    /** Manufacturer Name String - 0x2a29 */
    private String manufacturerName;
    /** Model Number String - 0x2a24 */
    private String modelNumber;
    /** Serial Number String - 0x2a25 */
    private String serialNumber;
    /** Hardware Revision String - 0x2a27 */
    private String hardwareRevision;
    /** Firmware Revision String - 0x2a26 */
    private String firmwareRevision;
    /** Software Revision String - 0x2a28 */
    private String softwareRevision;
    /** System ID - 0x2a23 */
    private byte[] systemId;
    /** IEEE 11073-20601 Regulatory Certification Data List */
    private byte[] regulatoryCertData;

    public DeviceInformation() {
        this.manufacturerName = "";
        this.modelNumber = "";
        this.serialNumber = "";
        this.hardwareRevision = "";
        this.firmwareRevision = "";
        this.softwareRevision = "";
        this.systemId = new byte[1];
        this.regulatoryCertData = new byte[1];
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getHardwareRevision() {
        return hardwareRevision;
    }

    public void setHardwareRevision(String hardwareRevision) {
        this.hardwareRevision = hardwareRevision;
    }

    public String getFirmwareRevision() {
        return firmwareRevision;
    }

    public void setFirmwareRevision(String firmwareRevision) {
        this.firmwareRevision = firmwareRevision;
    }

    public String getSoftwareRevision() {
        return softwareRevision;
    }

    public void setSoftwareRevision(String softwareRevision) {
        this.softwareRevision = softwareRevision;
    }

    public byte[] getSystemId() {
        return systemId;
    }

    public void setSystemId(byte[] systemId) {
        this.systemId = systemId;
    }

    public byte[] getRegulatoryCertData() {
        return regulatoryCertData;
    }

    public void setRegulatoryCertData(byte[] regulatoryCertData) {
        this.regulatoryCertData = regulatoryCertData;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Manufacturer Name: ").append(manufacturerName).append("\n");
        builder.append("Model Number: ").append(modelNumber).append("\n");
        builder.append("Serial Number: ").append(serialNumber).append("\n");
        builder.append("Hardware Revision: ").append(hardwareRevision).append("\n");
        builder.append("Firmware Revision: ").append(firmwareRevision).append("\n");
        builder.append("Software Revision: ").append(softwareRevision).append("\n");
        builder.append("System ID: ");
        for (byte elem : systemId) {
            builder.append(String.format("%02x", elem));
        }
        builder.append("\n");
        builder.append("Regulatory Cert Data List: ");
        for (byte elem : regulatoryCertData) {
            builder.append(String.format("%02x", elem));
        }
        return builder.toString();
    }
}
