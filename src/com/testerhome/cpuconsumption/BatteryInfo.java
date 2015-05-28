package com.testerhome.cpuconsumption;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

import com.android.internal.os.PowerProfile;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;


public class BatteryInfo {
	private Context mContext;
	private PowerProfile mPowerProfile;
	private static final String TAG = "BatteryInfo";
	
	public BatteryInfo(Context context) {
		mContext = context;
		mPowerProfile = new PowerProfile(context);
	}
	
	public double getBatteryCapacity() {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = mContext.registerReceiver(null, ifilter);
		double status = (double) batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / 100;
		double batteryCapacity = status * mPowerProfile.getBatteryCapacity();
		Log.i(TAG, "bat" + status + batteryCapacity);

		return batteryCapacity;

	}
	
	//����appʹ��cpu�ĺĵ磬ͨ������Ƶ���µ�ƽ�������ó�����ֵ
	public double measurePowerUsage(long[][] timeInStateBefor, HashMap<String, Long> appCpuTimeBefor) {
		HashMap<String, Long> appCpuTimeAfter = new HashMap<String, Long>();

//		Log.i(TAG, "timeInStateBefor" + timeInStateBefor[0][0]);
//		Log.i(TAG, "timeInStateBefor" + timeInStateBefor[0][1]);
//		Log.i(TAG, "appCpuTimeBefor" + appCpuTimeBefor.get("targetAppTime") + "  " + appCpuTimeBefor.get("totalTime"));
		
		appCpuTimeAfter = getAppCpuTime();
//		Log.i(TAG, "appCpuTimeBefor" + appCpuTimeBefor.get("targetAppTime") + "  " + appCpuTimeBefor.get("totalTime"));

//		Log.i(TAG, "timeInStateBefor" + timeInStateBefor[0][0]);
//		Log.i(TAG, "timeInStateBefor" + timeInStateBefor[0][1]);
		long[][] timeInStateAfter;
		timeInStateAfter = getTimeInState();
		
//		Log.i(TAG, "appCpuTimeAfter" + appCpuTimeAfter.get("targetAppTime") + "  " + appCpuTimeAfter.get("totalTime"));
//		Log.i(TAG, "timeInStateAfter" + timeInStateAfter[0][0]);
//		Log.i(TAG, "timeInStateAfter" + timeInStateAfter[0][1]);
		
		double totalPower = 0;		
		double targetAppTimeAfter = appCpuTimeAfter.get("targetAppTime");
		double targetAppTimeBefor = appCpuTimeBefor.get("targetAppTime");
		Log.i(TAG, "appCpuTimeAfter " + targetAppTimeAfter);
		Log.i(TAG, "appCpuTimeBefor " + targetAppTimeBefor);

		double totalTimeAfter = appCpuTimeAfter.get("totalTime");
		double totalTimeBefor = appCpuTimeBefor.get("totalTime");
		Log.i(TAG, "totalTimeAfter " + totalTimeAfter);
		Log.i(TAG, "totalTimeBefor " + totalTimeBefor);
		
		double ratio = (targetAppTimeAfter - targetAppTimeBefor) / (totalTimeAfter - totalTimeBefor);
		Log.i(TAG, "ratio" + ratio);
		double[] currentSteps = getCurrentSteps();
//		Log.i(TAG, "timeInStateAfter.length" + timeInStateAfter.length);
//		Log.i(TAG, "timeInStateBefor.length" + timeInStateBefor.length);
//		Log.i(TAG, "currentSteps.length" + currentSteps.length);
		for (int i = 0; i < timeInStateAfter.length; i++) {
			double power = (timeInStateAfter[i][1] - timeInStateBefor[i][1]) / 100 * currentSteps[timeInStateAfter.length - 1 - i];
			totalPower += power;
//			Log.i(TAG, "totalPower" + totalPower + " Feq" + timeInStateAfter[i][0] + "getCurrentSteps" + currentSteps[timeInStateAfter.length - 1 - i]);
		}
		return totalPower * ratio / 3600;
	}
	//���CPU����Ƶ���µ���mA
	private double[] getCurrentSteps() {
//		final int speedSteps = mPowerProfile.getNumSpeedSteps(); //�м���cpuƵ�ʵȼ�
//		final double[] powerCpuNormal = new double[speedSteps];
//		for (int p = 0; p < speedSteps; p++) {
//			powerCpuNormal[p] = mPowerProfile.getAveragePower(PowerProfile.POWER_CPU_ACTIVE, p);
//		}
		final int speedSteps = 5; //�м���cpuƵ�ʵȼ�
		final double[] powerCpuNormal = new double[speedSteps];
		for (int p = 0; p < speedSteps; p++) {
			double ab = 20;
			powerCpuNormal[p] = (double) ((double) 100 + (ab * p));
//			Log.i(TAG, "powerCpuNormal" + p + " " + powerCpuNormal[p]);
		}
		return powerCpuNormal;
	}
	
	
	//���appʹ�õ�CPUʱ��
	public HashMap<String, Long> getAppCpuTime() {
		HashMap<String, Long> appCpuTime = new HashMap<String, Long>();

		long totalTime = 0;
		long targetProcessTime = 0;

		ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> runningApps = am.getRunningAppProcesses(); //�����е�app
		
		for (RunningAppProcessInfo info : runningApps) {
			long time = getAppProcessTime(info.pid); //������ʱ��
//			Log.i(TAG, "time" + time);
            totalTime += time;
//			Log.i(TAG, "totalTime" + totalTime);
            if (info.processName.contains("tv.danmaku.bili")) {
            	targetProcessTime += time;
            }
		}
//		Log.i(TAG, "ftotalTime" + totalTime);

		appCpuTime.put("totalTime", totalTime);
		appCpuTime.put("targetAppTime", targetProcessTime);
//		Log.i(TAG, "appCpuTime" + appCpuTime.toString());

		return appCpuTime;
	}
	
	//���ؽ�������ʱ�䣬���ļ�
	private long getAppProcessTime(int pid) {
		FileInputStream in = null;
		String ret = null;
		try {
			in = new FileInputStream("/proc/" + pid + "/stat");
			byte[] buffer = new byte[1024];
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			int len = 0;
			while ((len = in.read(buffer)) != -1) {
				os.write(buffer, 0, len);
			}
			ret = os.toString();
			os.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (ret == null) {
			return 0;
		}
		
		String[] s = ret.split(" ");
		if (s == null || s.length < 17) {
			return 0;
		}
		
		long utime = string2Long(s[13]);
		long stime = string2Long(s[14]);
		long cutime = string2Long(s[15]);
		long cstime = string2Long(s[16]);
		
//		Log.i(TAG, "processTime" + (utime + stime + cutime + cstime));

		return utime + stime + cutime + cstime;
	}
	
	private long string2Long(String s) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
		}
		return 0;
	}


	//���CPU�ڸ���Ƶ��������ʱ��,��λ10����
	public long[][] getTimeInState() {
		excutor("cat /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
	      StringBuffer output = new StringBuffer();
	      output = excutor("cat /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");

//	      Process p;

//	      try {
//	        p = Runtime.getRuntime().exec("cat /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
//	        p.waitFor();
//
//	        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
//	 
//	        String line = "";
//	        while ((line = reader.readLine())!= null) {
//	          output.append(line + "\n");
//	        }	 
//	      } catch (Exception e) {
//	        e.printStackTrace();
//	      }
	      
	      String[] str = output.toString().split("\n");
//    	  Log.i(TAG, "str.length" + str.length);
	      
	  	  long[][] timeInState;
	      timeInState = new long[str.length][2];
	      for (int i = 0; i < str.length; i++) {
	    	  timeInState[i][0]= string2Long(str[i].split(" ")[0]); //Ƶ��
	    	  timeInState[i][1] = string2Long(str[i].split(" ")[1]); //ʱ��
//	    	  Log.i(TAG, timeInState[i][0] + " " + timeInState[i][1]);

	      }	      
	      return timeInState;
	      
	    }
	
	public StringBuffer excutor(String cmd) {
	      StringBuffer output = new StringBuffer();
	      Process p;
	      
	      try {
	        p = Runtime.getRuntime().exec(cmd);
	        p.waitFor();

	        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	 
	        String line = "";
	        while ((line = reader.readLine())!= null) {
	          output.append(line + "\n");
	        }	 
	      } catch (Exception e) {
	        e.printStackTrace();
	      }
	      return output;
	}
	
}