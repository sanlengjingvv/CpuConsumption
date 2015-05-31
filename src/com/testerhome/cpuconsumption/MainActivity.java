package com.testerhome.cpuconsumption;

import com.testerhome.cpuconsumption.BatteryInfo;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = "BatteryInfo";

	private LinearLayout touchInterceptor;
	private TextView dfmSuperConsumption;
	private TextView dfmConsumption;
	private Button begin;
	private Button finish;
    private RadioButton radioChooseDFMSuper, radioChooseDFM; //用于选择弹幕绘制模式
    private EditText avid; //av号
    private EditText timeElapse; //测试时间
    private EditText packageName; //包名

	private BatteryInfo info;
	private long[][] timeInStateBefor; //测试开始时Cpu在各个频率下运行时间
	private HashMap<String, Long> appCpuTimeBefor = new HashMap<String, Long>(); //测试开始时app运行时间
	private double batteryCapacityBefor; //测试开始时剩余电量
	private double powerUsage; //测试期间app在Cpu上消耗的电量
	private double batteryConsumption; //测试期间
	private String danmaku; //弹幕绘制模式
	private String danmakuParameter; //拼接的Uiautomator参数，弹幕绘制模式
	private String avidParameter; //拼接的Uiautomator参数，av号
	private String timeElapseParameter; //拼接的Uiautomator参数，测试时间
	private String packageNameString;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //将assets下的Uiautomator包复制到/data/data/com.testerhome.cpuconsumption/files/
	    new Thread(new Runnable () {
	    	@Override
	    	public void run() {
	    		copyFile("VideoPlayerTest.jar");
	    	}
	    }).start();
	    
    	info = new BatteryInfo(this);
    	//检查PowerFile和time_in_state里的频率数是不是一致
    	if (info.getCurrentSteps().length != info.getTimeInState().length)
    			Toast.makeText(MainActivity.this, "啊咧，时空坐标好像对不上的说。", Toast.LENGTH_LONG).show();
    	
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
				//启动APP
			    Intent intent = new Intent(); 
			  	PackageManager packageManager = getPackageManager(); 
			  	intent = packageManager.getLaunchIntentForPackage(packageNameString); 
			  	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP) ; 
			  	startActivity(intent);
			  	Log.i("BatteryInfo", "packageNameString "+ packageNameString);
			  	if (packageNameString.equals("tv.danmaku.bili")) {
				  	if (radioChooseDFMSuper.isChecked()) {
				  		danmaku = "DFMSuper"; //超烈焰弹幕使
				  		danmakuParameter = " -e danmaku " + "DFMSuper";
				  	} else if (radioChooseDFM.isChecked()) {
				  		danmaku = "DFM"; //烈焰弹幕使
				  		danmakuParameter = " -e danmaku " + "DFM";
				  	}

				  	//执行uiautomator脚本
				    new Thread(new Runnable () {
				    	@Override
				    	public void run() {
				    		Process rt;
							try {
								rt = Runtime.getRuntime().exec("su");
					    		DataOutputStream os = new DataOutputStream(rt.getOutputStream());
					    		String appDefaultPath = MainActivity.this.getFilesDir().getPath(); // /data/data/<PackageName>/files/
					            Log.i(TAG, "uiautomator runtest " + appDefaultPath +"VideoPlayerTest.jar -c tv.danmaku.VideoPlayerTest " + danmakuParameter + avidParameter + timeElapseParameter);
					    		os.writeBytes("uiautomator runtest " + appDefaultPath +"VideoPlayerTest.jar -c tv.danmaku.VideoPlayerTest " + danmakuParameter + avidParameter + timeElapseParameter + "\n");
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
				batteryConsumption = batteryCapacityBefor - info.getBatteryCapacity();
				powerUsage = info.measurePowerUsage(packageNameString, timeInStateBefor, appCpuTimeBefor);
				BigDecimal powerUsageBigDecimal = new BigDecimal(powerUsage).setScale(2, RoundingMode.HALF_UP); //四舍五入
				BigDecimal batteryConsumptionBigDecimal = new BigDecimal(batteryConsumption).setScale(2, RoundingMode.HALF_UP); //四舍五入

				String consumptionResult = "Cpu耗电" + powerUsageBigDecimal.toString() + "mAh" + " 整机耗电" + batteryConsumptionBigDecimal + "mAh";
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
			}
		});
	}
	
	@Override
    protected void onResume() {
		super.onResume();
		touchInterceptor.requestFocus(); //背景获得焦点
	}

	//从assets文件夹中复制文件
	private void copyFile(String sourceFileName) {
	    AssetManager assetManager = getAssets();

	    InputStream in = null;
	    FileOutputStream out = null;
        try {
            Log.i(TAG, "start copyFile");
            out = openFileOutput("VideoPlayerTest.jar", Context.MODE_PRIVATE);;
            in = assetManager.open(sourceFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, read);
            }
	        Log.i(TAG, "finish copy");
        } catch (Exception e)
	    {
	        e.printStackTrace();
	    } finally {
			try {
				if (out != null) {
			        in.close();
			        in = null;
				}
				if (out != null) {
			        out.flush();
			        out.close();
			        out = null;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

