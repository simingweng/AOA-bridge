package com.bnj;

import org.apache.commons.lang3.ArrayUtils;
import org.usb4java.*;

import java.nio.ByteBuffer;

public class Main {

    private static final short[] ACCESSORY_VIDS = {0x04E8};
    private static final short[] ACCESSORY_PIDS = {0x2D00, 0x2D01, 0x2D04, 0x2D05};

    public static void main(String[] args) {
        // Create the libusb context
        Context context = new Context();

        // Initialize the libusb context
        int result = LibUsb.init(context);
        if (result < 0) {
            throw new LibUsbException("Unable to initialize libusb", result);
        }

        // Read the USB device list
        DeviceList list = new DeviceList();
        result = LibUsb.getDeviceList(context, list);
        if (result < 0) {
            throw new LibUsbException("Unable to get device list", result);
        }

        try {
            // Iterate over all devices and list them
            for (Device device : list) {
                int address = LibUsb.getDeviceAddress(device);
                int busNumber = LibUsb.getBusNumber(device);
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result < 0) {
                    throw new LibUsbException(
                            "Unable to read device descriptor", result);
                }
                System.out.format(
                        "Bus %03d, Device %03d: Vendor %04x, Product %04x%n",
                        busNumber, address, descriptor.idVendor(),
                        descriptor.idProduct());
                if (ArrayUtils.contains(ACCESSORY_PIDS, descriptor.idProduct())) {
                    //this is a phone in accessory mode
                    //TODO start communication with the device
                } else if (ArrayUtils.contains(ACCESSORY_VIDS, descriptor.idVendor())) {
                    //this is a Samsung device, we assume it's a smartphone and it's not in accessory mode yet
                    DeviceHandle handle = new DeviceHandle();
                    result = LibUsb.open(device, handle);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to open device", result);
                    }
                    result = LibUsb.claimInterface(handle, 0);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to claim interface 0", result);
                    }
                    ByteBuffer protocolVersion = ByteBuffer.allocate(2);
                    LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_IN | LibUsb.REQUEST_TYPE_VENDOR), (byte) 51, (short) 0, (short) 0, protocolVersion, 0);
                    System.out.print(protocolVersion.getShort());
                    LibUsb.releaseInterface(handle, 0);
                    LibUsb.close(handle);
                }
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // Deinitialize the libusb context
        LibUsb.exit(context);
    }
}
