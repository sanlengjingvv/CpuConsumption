package com.testerhome.cpuconsumption;

import com.testerhome.cpuconsumption.BatteryInfo;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

public class MainActivity extends Activity {
	private LinearLayout touchInterceptor;
	private TextView dfmSuperConsumption;
	private TextView dfmConsumption;
	private Button begin;
	private Button finish;
    private RadioButton radioChooseDFMSuper, radioChooseDFM; //弹幕绘制模式
    private EditText avid; //av号
    private EditText timeElapse; //测试时间
    private EditText packageName; //包名

	private BatteryInfo info;
	private long[][] timeInStateBefor;
	private HashMap<String, Long> appCpuTimeBefor = new HashMap<String, Long>();
	private double batteryCapacityBefor;
	private double powerUsage;
	private String danmaku;
	private String danmakuParameter;
	private String avidParameter;
	private String timeElapseParameter;
	private String packageNameString;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        //将assets下的uiautomator包复制到/data/local/tmp/
	    new Thread(new Runnable () {
	    	@Override
	    	public void run() {
				copyFile("VideoPlayerTest.jar", "/data/local/tmp/VideoPlayerTest.jar");
	    	}
	    }).start();
	    
    	info = new BatteryInfo(this);
    	avid = (EditText) findViewById(R.id.avid);
    	timeElapse = (EditText) findViewById(R.id.timeElapse);
    	packageName = (EditText) findViewById(R.id.packageName);
    	radioChooseDFMSuper = (RadioButton) findViewById(R.id.chooseDFMSuper);
    	radioChooseDFM = (RadioButton) findViewById(R.id.chooseDFM); 
		touchInterceptor = (LinearLayout)findViewById(R.id.rootLayout);

    	dfmSuperConsumption = (TextView) findViewById(R.id.DFMSuperConsumption);
    	dfmConsumption = (TextView) findViewById(R.id.DFMConsumption);
		begin = (Button) findViewById(R.id.begin);
		begin.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			  	packageNameString = packageName.getText().toString();

				avidParameter = " -e avid " + avid.getText().toString();
				timeElapseParameter =  " -e timeElapse " + timeElapse.getText().toString();

				timeInStateBefor = info.getTimeInState();
				appCpuTimeBefor = info.getAppCpuTime(packageNameString);
				batteryCapacityBefor = info.getBatteryCapacity();
			    Intent intent = new Intent(); 
			  	PackageManager packageManager = getPackageManager(); 
			  	intent = packageManager.getLaunchIntentForPackage(packageNameString); 
			  	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP) ; 
			  	startActivity(intent);
			  	Log.i("BatteryInfo", "packageNameString "+ packageNameString);
			  	Log.i("BatteryInfo", "packageNameString == \"tv.danmaku.bili\" "+ (packageNameString == "tv.danmaku.bili"));
			  	if (packageNameString.equals("tv.danmaku.bili")) {
				  	if (radioChooseDFMSuper.isChecked()) {
				  		danmaku = "DFMSuper"; //超烈焰弹幕使
				  		danmakuParameter = " -e danmaku " + "DFMSuper";
				  	} else if (radioChooseDFM.isChecked()) {
				  		danmaku = "DFM"; //烈焰弹幕使
				  		danmakuParameter = " -e danmaku " + "DFM";
				  	}
				  	
				    new Thread(new Runnable () {
				    	@Override
				    	public void run() {
				    		Process rt;
							try {
								rt = Runtime.getRuntime().exec("su");
					    		DataOutputStream os = new DataOutputStream(rt.getOutputStream());
					    		os.writeBytes("uiautomator runtest VideoPlayerTest.jar -c tv.danmaku.VideoPlayerTest " + danmakuParameter + avidParameter + timeElapseParameter + "\n");
					    		os.flush();
					    		os.writeBytes("exit\n");
					    		Log.i("BatteryInfo", "uiautomator");
							} catch (IOException e) {
								e.printStackTrace();
							}
				    	}
				    }).start();
			  	}
			}
		});
		
		finish = (Button) findViewById(R.id.finish);
		finish.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				powerUsage = info.measurePowerUsage(packageNameString, timeInStateBefor, appCpuTimeBefor);
				BigDecimal powerUsageBigDecimal = new BigDecimal(powerUsage);  
				powerUsageBigDecimal = powerUsageBigDecimal.setScale(2, RoundingMode.HALF_UP);
				String consumptionResult = "Cpu耗电" + powerUsageBigDecimal.toString() + "mAh" + " 整机耗电" + (batteryCapacityBefor - info.getBatteryCapacity()) + "mAh";
			  	if (danmaku == "DFMSuper") {
				  	dfmSuperConsumption.setText(consumptionResult);
			  	} else if (danmaku == "DFM") {
			  		dfmConsumption.setText(consumptionResult);
			  	}
				
			}
		});
		
		//背景被点击时获得焦点
		touchInterceptor.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View view, MotionEvent event) {
				   switch (event.getAction()) {
				    case MotionEvent.ACTION_DOWN:
		                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE); 
		                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
						touchInterceptor.requestFocus();
				    case MotionEvent.ACTION_UP:
						view.performClick();
				    default:
				        break;
				    }
				    return true;
//				if (event.getAction() == MotionEvent.ACTION_DOWN) {
//	                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE); 
//	                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
//					touchInterceptor.requestFocus();
//				} else if (event.getAction() == MotionEvent.ACTION_UP) {
//				}
//				return false;
			}
		});
	}
	
	@Override
    protected void onResume() {
		super.onResume();
		touchInterceptor.requestFocus(); //背景获得焦点
	}

	//从assets文件夹中复制文件
	private boolean copyFile(String sourceFileName, String destFileName)
	{
	    AssetManager assetManager = getAssets();

	    File destFile = new File(destFileName);
	    if(destFile.exists())
	    	destFile.delete();
//	    File destParentDir = destFile.getParentFile();
//	    destParentDir.mkdir();

	    InputStream in = null;
	    OutputStream out = null;
	    try
	    {
	        in = assetManager.open(sourceFileName);
	        out = new FileOutputStream(destFile);

	        byte[] buffer = new byte[1024];
	        int read;
	        while ((read = in.read(buffer)) != -1)
	        {
	            out.write(buffer, 0, read);
	        }
	        in.close();
	        in = null;
	        out.flush();
	        out.close();
	        out = null;

	        return true;
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }

	    return false;
	}
}

