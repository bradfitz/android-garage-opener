/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/bradfitz/Documents/workspace/garagedoor/src/com/danga/garagedoor/IGarageScanService.aidl
 */
package com.danga.garagedoor;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface IGarageScanService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.danga.garagedoor.IGarageScanService
{
private static final java.lang.String DESCRIPTOR = "com.danga.garagedoor.IGarageScanService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IGarageScanService interface,
 * generating a proxy if needed.
 */
public static com.danga.garagedoor.IGarageScanService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
com.danga.garagedoor.IGarageScanService in = (com.danga.garagedoor.IGarageScanService)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new com.danga.garagedoor.IGarageScanService.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_isScanning:
{
data.enforceInterface(DESCRIPTOR);
boolean _result = this.isScanning();
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_setDebugMode:
{
data.enforceInterface(DESCRIPTOR);
boolean _arg0;
_arg0 = (0!=data.readInt());
this.setDebugMode(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setScanning:
{
data.enforceInterface(DESCRIPTOR);
boolean _arg0;
_arg0 = (0!=data.readInt());
this.setScanning(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_registerCallback:
{
data.enforceInterface(DESCRIPTOR);
com.danga.garagedoor.IGarageScanCallback _arg0;
_arg0 = com.danga.garagedoor.IGarageScanCallback.Stub.asInterface(data.readStrongBinder());
this.registerCallback(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_unregisterCallback:
{
data.enforceInterface(DESCRIPTOR);
com.danga.garagedoor.IGarageScanCallback _arg0;
_arg0 = com.danga.garagedoor.IGarageScanCallback.Stub.asInterface(data.readStrongBinder());
this.unregisterCallback(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_openGarageNow:
{
data.enforceInterface(DESCRIPTOR);
this.openGarageNow();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.danga.garagedoor.IGarageScanService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public boolean isScanning() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_isScanning, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void setDebugMode(boolean debugMode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((debugMode)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_setDebugMode, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
// scan forever, never opening the door

public void setScanning(boolean isEnabled) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((isEnabled)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_setScanning, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void registerCallback(com.danga.garagedoor.IGarageScanCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void unregisterCallback(com.danga.garagedoor.IGarageScanCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_unregisterCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void openGarageNow() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_openGarageNow, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_isScanning = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setDebugMode = (IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_setScanning = (IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_registerCallback = (IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_unregisterCallback = (IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_openGarageNow = (IBinder.FIRST_CALL_TRANSACTION + 5);
}
public boolean isScanning() throws android.os.RemoteException;
public void setDebugMode(boolean debugMode) throws android.os.RemoteException;
// scan forever, never opening the door

public void setScanning(boolean isEnabled) throws android.os.RemoteException;
public void registerCallback(com.danga.garagedoor.IGarageScanCallback callback) throws android.os.RemoteException;
public void unregisterCallback(com.danga.garagedoor.IGarageScanCallback callback) throws android.os.RemoteException;
public void openGarageNow() throws android.os.RemoteException;
}
