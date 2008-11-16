/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/bradfitz/Documents/workspace/garagedoor/src/com/danga/garagedoor/IGarageScanCallback.aidl
 */
package com.danga.garagedoor;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface IGarageScanCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.danga.garagedoor.IGarageScanCallback
{
private static final java.lang.String DESCRIPTOR = "com.danga.garagedoor.IGarageScanCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IGarageScanCallback interface,
 * generating a proxy if needed.
 */
public static com.danga.garagedoor.IGarageScanCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
com.danga.garagedoor.IGarageScanCallback in = (com.danga.garagedoor.IGarageScanCallback)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new com.danga.garagedoor.IGarageScanCallback.Stub.Proxy(obj);
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
case TRANSACTION_logToClient:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.logToClient(_arg0);
return true;
}
case TRANSACTION_onScanResults:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.onScanResults(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.danga.garagedoor.IGarageScanCallback
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
public void logToClient(java.lang.String stuff) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(stuff);
mRemote.transact(Stub.TRANSACTION_logToClient, _data, null, IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
public void onScanResults(java.lang.String scanResults) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(scanResults);
mRemote.transact(Stub.TRANSACTION_onScanResults, _data, null, IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_logToClient = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onScanResults = (IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void logToClient(java.lang.String stuff) throws android.os.RemoteException;
public void onScanResults(java.lang.String scanResults) throws android.os.RemoteException;
}
