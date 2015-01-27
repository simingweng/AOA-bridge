package com.bnj;

import org.apache.commons.lang3.ArrayUtils;
import org.usb4java.*;
import org.usb4java.javax.adapter.UsbServicesAdapter;

import javax.usb.*;
import javax.usb.event.UsbServicesEvent;
import javax.usb.event.UsbServicesListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final short[] ACCESSORY_VIDS = {0x04E8};
    private static final short[] ACCESSORY_PIDS = {0x2D00, 0x2D01, 0x2D04, 0x2D05};
    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> future;
    private static UsbInterface iface;
    private static UsbPipe pipe;

    private static UsbServicesListener usbServicesListener = new UsbServicesAdapter() {
        @Override
        public void usbDeviceAttached(UsbServicesEvent event) {
            super.usbDeviceAttached(event);
            UsbDevice device = event.getUsbDevice();
            if (ArrayUtils.contains(ACCESSORY_VIDS, device.getUsbDeviceDescriptor().idVendor())) {
                UsbControlIrp irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_IN | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 51, (short) 0, (short) 0);
                irp.setData(new byte[2]);
                try {
                    device.syncSubmit(irp);
                    short version = ByteBuffer.wrap(irp.getData()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get();
                    System.out.println("device supports AOA protocol version " + version);
                    if (version == 1 || version == 2) {
                        triggerAccessoryMode(device);
                    }
                } catch (UsbException e) {
                    e.printStackTrace();
                }
            } else if (ArrayUtils.contains(ACCESSORY_PIDS, device.getUsbDeviceDescriptor().idProduct())) {
                System.out.println(device.getUsbDeviceDescriptor().iSerialNumber() + " is in accessory mode");
                try {
                    startSendingData(device);
                } catch (UsbException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void usbDeviceDetached(UsbServicesEvent event) {
            super.usbDeviceDetached(event);
            if (future != null) {
                future.cancel(true);
                future = null;
            }
            pipe = null;
            iface = null;
        }
    };

    public static void main(String[] args) {
        doInHighLevel();
    }

    private static void doInHighLevel() {
        try {
            UsbHostManager.getUsbServices().addUsbServicesListener(usbServicesListener);
            System.out.println("press enter to exit");
            System.in.read();
            UsbHostManager.getUsbServices().removeUsbServicesListener(usbServicesListener);
            if (future != null) {
                future.cancel(true);
                future = null;
            }
            if (pipe != null) {
                pipe.close();
                pipe = null;
            }
            if (iface != null) {
                iface.release();
                iface = null;
            }
            System.exit(0);
        } catch (UsbException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void triggerAccessoryMode(UsbDevice device) throws UsbException {
        System.out.println("trigger " + device.getUsbDeviceDescriptor().iSerialNumber() + " to accessory mode");
        List<UsbControlIrp> irps = new ArrayList<>();
        UsbControlIrp irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 0);
        irp.setData("BNJ".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 1);
        irp.setData("BeagleBone Companion".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 2);
        irp.setData("A single board computer to manage a set of smart phones".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 3);
        irp.setData("1.0".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 4);
        irp.setData("http://bamboo1.cos.ds.jdsu.net:8085/browse/DTA-DM-9/artifact/JOB1/apk/dci-manager-service-release-unsigned.apk".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 52, (short) 0, (short) 5);
        irp.setData("1234".getBytes());
        irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 53, (short) 0, (short) 0);
        irps.add(irp);
        device.syncSubmit(irps);
    }

    private static void startSendingData(UsbDevice device) throws UsbException {
        iface = device.getActiveUsbConfiguration().getUsbInterface((byte) 0);
        iface.claim();
        for (Object obj : iface.getUsbEndpoints()) {
            UsbEndpoint endpoint = (UsbEndpoint) obj;
            if (endpoint.getDirection() == UsbConst.ENDPOINT_DIRECTION_OUT) {
                pipe = endpoint.getUsbPipe();
                pipe.open();
                future = executorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int numOfBytes = pipe.syncSubmit("hello".getBytes());
                            System.out.println(numOfBytes + " bytes sent");
                        } catch (UsbException e) {
                            e.printStackTrace();
                        }
                    }
                }, 0, 3, TimeUnit.SECONDS);
            }
        }
    }

    private static void doInLowLevel() {
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
                    DeviceHandle handle = new DeviceHandle();
                    LibUsb.open(device, handle);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to open device", result);
                    }
                    ConfigDescriptor configDescriptor = new ConfigDescriptor();
                    result = LibUsb.getActiveConfigDescriptor(device, configDescriptor);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to get active configuration", result);
                    }
                    EndpointDescriptor[] endpointDescriptors = configDescriptor.iface()[0].altsetting()[0].endpoint();
                    byte outputEndpoint = -1;
                    byte inputEndpoint = -1;
                    for (EndpointDescriptor endpointDescriptor : endpointDescriptors) {
                        if ((endpointDescriptor.bEndpointAddress() & LibUsb.ENDPOINT_DIR_MASK) == LibUsb.ENDPOINT_IN) {
                            inputEndpoint = endpointDescriptor.bEndpointAddress();
                        } else {
                            outputEndpoint = endpointDescriptor.bEndpointAddress();
                        }
                    }
                    LibUsb.freeConfigDescriptor(configDescriptor);
                    result = LibUsb.claimInterface(handle, 0);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to claim interface 0", result);
                    }
                    IntBuffer len = BufferUtils.allocateIntBuffer();
                    result = LibUsb.bulkTransfer(handle, outputEndpoint, wrapDirect("hello"), len, 0);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to send bulk transfer", result);
                    }
                    System.out.println(len.get() + " bytes transferred");
                    LibUsb.releaseInterface(handle, 0);
                    LibUsb.close(handle);
                } else if (ArrayUtils.contains(ACCESSORY_VIDS, descriptor.idVendor())) {
                    //this is a Samsung device, we assume it's a smartphone and it's not in accessory mode yet
                    DeviceHandle handle = new DeviceHandle();
                    result = LibUsb.open(device, handle);
                    if (result < 0) {
                        throw new LibUsbException(
                                "unable to open device", result);
                    }
                    ByteBuffer protocolVersion = ByteBuffer.allocateDirect(2);
                    protocolVersion.order(ByteOrder.LITTLE_ENDIAN);
                    LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_IN | LibUsb.REQUEST_TYPE_VENDOR), (byte) 51, (short) 0, (short) 0, protocolVersion, 0);
                    short ver = protocolVersion.getShort();
                    System.out.println("supported AOA protocol version " + ver);
                    if (ver == 1 || ver == 2) {
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 0, wrapDirect("BNJ"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 1, wrapDirect("BeagleBone Companion"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 2, wrapDirect("A single board computer to manage a set of smart phones"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 3, wrapDirect("1.0"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 4, wrapDirect("http://bamboo1.cos.ds.jdsu.net:8085/browse/DTA-DM-9/artifact/JOB1/apk/dci-manager-service-release-unsigned.apk"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 52, (short) 0, (short) 5, wrapDirect("1234"), 0);
                        LibUsb.controlTransfer(handle, (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR), (byte) 53, (short) 0, (short) 0, ByteBuffer.allocateDirect(0), 0);
                    }
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

    private static ByteBuffer wrapDirect(String string) {
        byte[] bytes = string.getBytes();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes);
        buffer.rewind();
        return buffer;
    }
}
