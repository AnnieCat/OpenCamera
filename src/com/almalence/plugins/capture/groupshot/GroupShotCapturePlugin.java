/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is collection of files collectively known as Open Camera.

The Initial Developer of the Original Code is Almalence Inc.
Portions created by Initial Developer are Copyright (C) 2013 
by Almalence Inc. All Rights Reserved.
*/

package com.almalence.plugins.capture.groupshot;

import java.nio.ByteBuffer;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.CountDownTimer;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

/* <!-- +++
import com.almalence.opencam_plus.CameraController;
import com.almalence.opencam_plus.MainScreen;
import com.almalence.opencam_plus.PluginCapture;
import com.almalence.opencam_plus.PluginManager;
import com.almalence.opencam_plus.R;
+++ --> */
// <!-- -+-
import com.almalence.opencam.cameracontroller.CameraController;
import com.almalence.opencam.MainScreen;
import com.almalence.opencam.PluginCapture;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.R;
//-+- -->
import com.almalence.SwapHeap;
import com.almalence.YuvImage;

/***
Implements group shot capture plugin - captures predefined number of images
***/

public class GroupShotCapturePlugin extends PluginCapture
{
	//defaul val. value should come from config
	private int imageAmount = 5;
    private int pauseBetweenShots = 0;
    
    private int imagesTaken=0;
    
    private String sImagesAmountPref;
    private String sPauseBetweenShotsPref;

	public GroupShotCapturePlugin()
	{
		super("com.almalence.plugins.groupshotcapture",
			  R.xml.preferences_capture_groupshot,
			  0,
			  0,
			  null);
	}
	
	@Override
	public void onCreate()
	{
		sImagesAmountPref = MainScreen.thiz.getResources().getString(R.string.Preference_GroupShotImagesAmountPref);
		sPauseBetweenShotsPref = MainScreen.thiz.getResources().getString(R.string.Preference_GroupShotPauseBetweenShotsPref);
	}
	
	@Override
	public void onResume()
	{
		takingAlready = false;
		imagesTaken=0;
		inCapture = false;
		refreshPreferences();
		
		MainScreen.captureYUVFrames = true;
	}
	
	@Override
	public void onGUICreate()
	{
		MainScreen.guiManager.showHelp(MainScreen.thiz.getString(R.string.GroupShot_Help_Header), MainScreen.thiz.getResources().getString(R.string.GroupShot_Help), R.drawable.plugin_help_groupshot, "groupshotRemovalShowHelp");
	}
	
	private void refreshPreferences()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.mainContext);
		imageAmount = Integer.parseInt(prefs.getString(sImagesAmountPref, "7"));
		pauseBetweenShots = Integer.parseInt(prefs.getString(sPauseBetweenShotsPref, "750"));
	}
	
	public boolean delayedCaptureSupported(){return true;}
	
	public void takePicture()
	{
		if(!inCapture)
		{
			refreshPreferences();
			inCapture = true;
			takingAlready = true;
			
			if (imagesTaken==0 || pauseBetweenShots==0)
			{
				Message msg = new Message();
				msg.arg1 = PluginManager.MSG_NEXT_FRAME;
				msg.what = PluginManager.MSG_BROADCAST;
				MainScreen.H.sendMessage(msg);
			}
			else
			{
				new CountDownTimer(pauseBetweenShots, pauseBetweenShots) {
				     public void onTick(long millisUntilFinished) {}
				     public void onFinish() 
				     {
				    	 Message msg = new Message();
				    	 msg.arg1 = PluginManager.MSG_NEXT_FRAME;
				    	 msg.what = PluginManager.MSG_BROADCAST;
				    	 MainScreen.H.sendMessage(msg);
				     }
				  }.start();
			}
		}
	}

	@Override
	public void onPictureTaken(byte[] paramArrayOfByte, Camera paramCamera)
	{
		imagesTaken++;
		int frame_len = paramArrayOfByte.length;
		int frame = SwapHeap.SwapToHeap(paramArrayOfByte);
    	
    	if (frame == 0)
    	{
    		Log.i("Group Shot", "Load to heap failed");
    		
    		Message message = new Message();
    		message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			MainScreen.thiz.MuteShutter(false);
			inCapture = false;
			return;
    	}
    	String frameName = "frame" + imagesTaken;
    	String frameLengthName = "framelen" + imagesTaken;
    	
    	PluginManager.getInstance().addToSharedMem(frameName+String.valueOf(SessionID), String.valueOf(frame));
    	PluginManager.getInstance().addToSharedMem(frameLengthName+String.valueOf(SessionID), String.valueOf(frame_len));
    	
    	if(imagesTaken == 1)
    		PluginManager.getInstance().addToSharedMem_ExifTagsFromJPEG(paramArrayOfByte, SessionID, -1);
		
		try
		{
			paramCamera.startPreview();
		}
		catch (RuntimeException e)
		{
			Log.i("Group Shot", "StartPreview fail");
			
			Message message = new Message();
			message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			MainScreen.thiz.MuteShutter(false);
			inCapture = false;
			return;
		}
		if (imagesTaken < imageAmount)
		{
			inCapture = false;
			MainScreen.H.sendEmptyMessage(PluginManager.MSG_TAKE_PICTURE);
		}
		else
		{
			PluginManager.getInstance().addToSharedMem("amountofcapturedframes"+String.valueOf(SessionID), String.valueOf(imagesTaken));
			
			Message message = new Message();
			message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			
			//call timer to reset inCapture 
			new CountDownTimer(5000, 5000) {
			     public void onTick(long millisUntilFinished) {}
			     public void onFinish() 
			     {
			    	 inCapture = false;
			     }
			  }.start();
		}
		takingAlready = false;
	}
	
	
	@TargetApi(19)
	@Override
	public void onImageAvailable(Image im)
	{
		imagesTaken++;
		
		ByteBuffer Y = im.getPlanes()[0].getBuffer();
		ByteBuffer U = im.getPlanes()[1].getBuffer();
		ByteBuffer V = im.getPlanes()[2].getBuffer();

		if ( (!Y.isDirect()) || (!U.isDirect()) || (!V.isDirect()) )
		{
			Log.e("CapturePlugin", "Oops, YUV ByteBuffers isDirect failed");
			return;
		}
		
		
		// Note: android documentation guarantee that:
		// - Y pixel stride is always 1
		// - U and V strides are the same
		//   So, passing all these parameters is a bit overkill
		int status = YuvImage.CreateYUVImage(Y, U, V,
				im.getPlanes()[0].getPixelStride(),
				im.getPlanes()[0].getRowStride(),
				im.getPlanes()[1].getPixelStride(),
				im.getPlanes()[1].getRowStride(),
				im.getPlanes()[2].getPixelStride(),
				im.getPlanes()[2].getRowStride(),
				MainScreen.getImageWidth(), MainScreen.getImageHeight(), 0);
		
		if (status != 0)
			Log.e("CapturePlugin", "Error while cropping: "+status);
		
		
		byte[] byte_frame = YuvImage.GetByteFrame(0);
		int frame_len = byte_frame.length;
		int frame = SwapHeap.SwapToHeap(byte_frame);
		
    	if (frame == 0)
    	{
    		Log.i("Group Shot", "Load to heap failed");
    		
    		Message message = new Message();
    		message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			MainScreen.thiz.MuteShutter(false);
			inCapture = false;
			return;
    	}
    	String frameName = "frame" + imagesTaken;
    	String frameLengthName = "framelen" + imagesTaken;
    	
    	PluginManager.getInstance().addToSharedMem(frameName+String.valueOf(SessionID), String.valueOf(frame));
    	PluginManager.getInstance().addToSharedMem(frameLengthName+String.valueOf(SessionID), String.valueOf(frame_len));
    	
    	PluginManager.getInstance().addToSharedMem("isyuv"+String.valueOf(SessionID), String.valueOf(true));
    	
		try
		{
			CameraController.startCameraPreview();
		}
		catch (RuntimeException e)
		{
			Log.i("Group Shot", "StartPreview fail");
			
			Message message = new Message();
			message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			MainScreen.thiz.MuteShutter(false);
			inCapture = false;
			return;
		}
		if (imagesTaken < imageAmount)
		{
			inCapture = false;
			MainScreen.H.sendEmptyMessage(PluginManager.MSG_TAKE_PICTURE);
		}
		else
		{
			PluginManager.getInstance().addToSharedMem("amountofcapturedframes"+String.valueOf(SessionID), String.valueOf(imagesTaken));
			
			Message message = new Message();
			message.obj = String.valueOf(SessionID);
			message.what = PluginManager.MSG_CAPTURE_FINISHED;
			MainScreen.H.sendMessage(message);
			
			imagesTaken=0;
			
			inCapture = false;
		}
		takingAlready = false;	
	}
	
	@TargetApi(19)
	@Override
	public void onCaptureCompleted(CaptureResult result)
	{
		if(result.get(CaptureResult.REQUEST_ID) == requestID)
		{
			if(imagesTaken == 1)
	    		PluginManager.getInstance().addToSharedMem_ExifTagsFromCaptureResult(result, SessionID);
		}
	}
	
	
	@Override
	public void onAutoFocus(boolean paramBoolean)
	{
		if(takingAlready == true)
			takePicture();
	}

	@Override
	public boolean onBroadcast(int arg1, int arg2)
	{
		if (arg1 == PluginManager.MSG_NEXT_FRAME)
		{
			// play tick sound
			MainScreen.guiManager.showCaptureIndication();
    		MainScreen.thiz.PlayShutter();
    		try
    		{
    			requestID = CameraController.captureImage(1, CameraController.YUV);
    		}
    		catch(RuntimeException exp)
    		{
    			inCapture = false;
				takingAlready = false;
				Message msg = new Message();
    			msg.arg1 = PluginManager.MSG_CONTROL_UNLOCKED;
    			msg.what = PluginManager.MSG_BROADCAST;
    			MainScreen.H.sendMessage(msg);
    			
    			MainScreen.guiManager.lockControls = false;
    		}
			
			return true;
		}
		return false;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera paramCamera) {}
}
