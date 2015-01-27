package com.bnj;

import javax.usb.*;

/**
 * Created by simingweng on 27/1/15.
 */
public class AccessorySlave {

    private UsbDevice device;
    private UsbPipe inputPipe;
    private UsbPipe outputPipe;

    public AccessorySlave(UsbDevice device) {
        this.device = device;
    }

    public void openCommunication() throws UsbException {
        if (device == null) {
            throw new IllegalStateException();
        }
        UsbInterface iface = device.getActiveUsbConfiguration().getUsbInterface((byte) 0);
        iface.claim();
        for (Object obj : iface.getUsbEndpoints()) {
            UsbEndpoint endpoint = (UsbEndpoint) obj;
            if (endpoint.getDirection() == UsbConst.ENDPOINT_DIRECTION_OUT) {
                outputPipe = endpoint.getUsbPipe();
                outputPipe.open();
            } else {
                inputPipe = endpoint.getUsbPipe();
                inputPipe.open();
            }
        }
    }

    public void closeCommunication() throws UsbException {
        if (inputPipe != null && inputPipe.isOpen()) {
            inputPipe.close();
        }
        if (outputPipe != null && outputPipe.isOpen()) {
            outputPipe.close();
        }
        UsbInterface iface = device.getActiveUsbConfiguration().getUsbInterface((byte) 0);
        if (iface.isClaimed()) {
            iface.release();
        }
    }

    public int send(byte[] data) throws UsbException {
        if (outputPipe != null && outputPipe.isOpen()) {
            return outputPipe.syncSubmit(data);
        }
        throw new IllegalStateException();
    }

    public int receive(byte[] buffer) throws UsbException {
        if (inputPipe != null && inputPipe.isOpen()) {
            return inputPipe.syncSubmit(buffer);
        }
        throw new IllegalStateException();
    }

    public void exit() throws UsbException {
        closeCommunication();
        device = null;
    }
}
