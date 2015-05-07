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
import java.util.Map;
import java.util.concurrent.*;

public class Main {

    private static final short[] ACCESSORY_PIDS = {0x2D00, 0x2D01, 0x2D04, 0x2D05};
    private static ScheduledExecutorService dummyTxService = Executors.newSingleThreadScheduledExecutor();
    private static ExecutorService dummyRxService = Executors.newCachedThreadPool();
    private static Map<UsbDevice, AccessorySlave> slaves = new ConcurrentHashMap<>();
    private static Map<UsbDevice, Future<?>> rxFutureMap = new ConcurrentHashMap<>();

    private static UsbServicesListener usbServicesListener = new UsbServicesAdapter() {
        @Override
        public void usbDeviceAttached(UsbServicesEvent event) {
            super.usbDeviceAttached(event);
            UsbDevice device = event.getUsbDevice();
            short pid = device.getUsbDeviceDescriptor().idProduct();
            if (pid == 0)
                return;
            if (ArrayUtils.contains(ACCESSORY_PIDS, pid)) {
                try {
                    System.out.println(device.getSerialNumberString() + " is in accessory mode");
                    onNewAccessoryDevice(device);
                } catch (UsbException | UnsupportedEncodingException | RuntimeException | ClassNotFoundException | IllegalAccessException | NoSuchFieldException | InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                UsbControlIrp irp = device.createUsbControlIrp((byte) (UsbConst.REQUESTTYPE_DIRECTION_IN | UsbConst.REQUESTTYPE_TYPE_VENDOR), (byte) 51, (short) 0, (short) 0);
                irp.setData(new byte[2]);
                try {
                    device.syncSubmit(irp);
                    short version = ByteBuffer.wrap(irp.getData()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get();

                    if (version == 1 || version == 2) {
                        System.out.println("found AOA-compliant USB device " + device.getProductString() + " v" + version);
                        triggerAccessoryMode(device);
                    }
                } catch (UsbException | RuntimeException | UnsupportedEncodingException e) {
                    System.out.println("ignore non-AOA-compliant USB device");
                }
            }
        }

        @Override
        public void usbDeviceDetached(UsbServicesEvent event) {
            super.usbDeviceDetached(event);
            try {
                AccessorySlave slave = slaves.remove(event.getUsbDevice());
                if (slave != null) {
                    slave.exit();
                }
                Future<?> rxFuture = rxFutureMap.remove(event.getUsbDevice());
                if (rxFuture != null) {
                    rxFuture.cancel(true);
                }
            } catch (UsbException | RuntimeException e) {
                e.printStackTrace();
            }
        }
    };

    public static void main(String[] args) {
        try {
            UsbHostManager.getUsbServices().addUsbServicesListener(usbServicesListener);
            dummyTxService.scheduleAtFixedRate(() -> {
                try {
                    broadcastHeartBeat();
                } catch (UsbException e) {
                    e.printStackTrace();
                }
            }, 0, 3, TimeUnit.SECONDS);
            Thread.sleep(3 * 1000);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("tear down peacefully");
                    UsbHostManager.getUsbServices().removeUsbServicesListener(usbServicesListener);
                    dummyTxService.shutdownNow();
                    dummyRxService.shutdownNow();
                    for (AccessorySlave slave : slaves.values()) {
                        slave.exit();
                    }
                } catch (UsbException e) {
                    e.printStackTrace();
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

    private static void onNewAccessoryDevice(UsbDevice device) throws UsbException, UnsupportedEncodingException, ClassNotFoundException, IllegalAccessException, NoSuchFieldException, InterruptedException {
        AccessorySlave newSlave = new AccessorySlave(device);
        AccessorySlave previousSlave = slaves.put(device, newSlave);
        if (previousSlave != null) {
            previousSlave.exit();
        }
        newSlave.openCommunication();
        Future<?> previousFuture = rxFutureMap.put(device, dummyRxService.submit(new RxTask(newSlave)));
        if (previousFuture != null) {
            previousFuture.cancel(true);
        }
    }

    private static void broadcastHeartBeat() throws UsbException {
        for (AccessorySlave slave : slaves.values()) {
            slave.send("heart beat from accessory".getBytes());
        }
    }

    private static class RxTask implements Runnable {

        private AccessorySlave slave;

        public RxTask(AccessorySlave slave) {
            this.slave = slave;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[16384];
            int numOfBytes;
            System.out.println("start reading from device");
            while (!Thread.interrupted()) {
                try {
                    numOfBytes = slave.receive(buffer);
                    System.out.println(new String(buffer, 0, numOfBytes));
                } catch (UsbException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Rx task exits");
        }
    }
}
