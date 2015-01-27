package com.bnj;

import org.apache.commons.lang3.ArrayUtils;
import org.usb4java.javax.adapter.UsbServicesAdapter;

import javax.usb.*;
import javax.usb.event.UsbServicesEvent;
import javax.usb.event.UsbServicesListener;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Main {

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
            if (ArrayUtils.contains(ACCESSORY_PIDS, device.getUsbDeviceDescriptor().idProduct())) {
                try {
                    System.out.println(device.getSerialNumberString() + " is in accessory mode");
                    startSendingData(device);
                } catch (UsbException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
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
        try {
            UsbHostManager.getUsbServices().addUsbServicesListener(usbServicesListener);
            Thread.sleep(3 * 1000);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("tear down peacefully");
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
                    } catch (UsbException e) {
                        e.printStackTrace();
                    }
                }
            }));
        } catch (UsbException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void triggerAccessoryMode(UsbDevice device) throws UsbException {
        System.out.println("trigger " + device.getUsbDeviceDescriptor().iSerialNumber() + " to accessory mode");
        List<UsbControlIrp> irps = new ArrayList<>();
        UsbControlIrp irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 0);
        irp.setData("BNJ".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 1);
        irp.setData("BeagleBone Companion".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 2);
        irp.setData("A single board computer to manage a set of smart phones".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 3);
        irp.setData("1.0".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 4);
        irp.setData("https://gitlab.com/siming.weng/aoa-accessory-agent/wikis/home".getBytes());
        irps.add(irp);
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 52, (short) 0, (short) 5);
        irp.setData("1234".getBytes());
        irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, (byte) 53, (short) 0, (short) 0);
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
}
