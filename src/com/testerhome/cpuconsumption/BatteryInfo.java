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
	
	//计算剩余电量mAh，精度1%
	public double getBatteryCapacity() {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = mContext.registerReceiver(null, ifilter);
		double status = (double) batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / 100; //转换成百分比
		double batteryCapacity = status * mPowerProfile.getBatteryCapacity();
		
		Log.i(TAG, "getBatteryCapacity " + status + "% " + batteryCapacity);

		return batteryCapacity;
	}
	
	//计算app通过cpu消耗的电量
	public double measurePowerUsage(String packageNameString, long[][] timeInStateBefor, HashMap<String, Long> appCpuTimeBefor) {
		HashMap<String, Long> appCpuTimeAfter = new HashMap<String, Long>(); //从开机到结束测试时，targetAppTime是目标app消耗的cpu时间，totalTime是所有app消耗的cpu时间
		appCpuTimeAfter = getAppCpuTime(packageNameString);
		
		long[][] timeInStateAfter; ////从开机到结束测试时cpu在各个频率下消耗的时间
		timeInStateAfter = getTimeInState();

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
		Log.i(TAG, "timeInStateBefor.length " + timeInStateBefor.length);
		Log.i(TAG, "currentSteps.length " + currentSteps.length);
		//根据cpu在各个频率下的平均电流和消耗时间计算cpu总耗电
		for (int i = 0; i < timeInStateAfter.length; i++) {
			double power = (timeInStateAfter[i][1] - timeInStateBefor[i][1]) / 100 * currentSteps[timeInStateAfter.length - 1 - i]; //倒序是因为PowerFile和time_in_state排序相反, timeInState单位是10毫秒，除100转成秒
			totalPower += power;
		}
		
		return totalPower * ratio / 3600; //从秒转到小时
	}
	
	//返回cpu在各个频率下的平均电流
	protected double[] getCurrentSteps() {
		final int speedSteps = mPowerProfile.getNumSpeedSteps(); //cpu工作频率的数量
		final double[] powerCpuNormal = new double[speedSteps];
		for (int p = 0; p < speedSteps; p++) {
			powerCpuNormal[p] = mPowerProfile.getAveragePower(PowerProfile.POWER_CPU_ACTIVE, p);
		}

		return powerCpuNormal;
	}
	
	//返回目标app和所有app消耗的cpu时间
	public HashMap<String, Long> getAppCpuTime(String packageName) {
		HashMap<String, Long> appCpuTime = new HashMap<String, Long>();
		long totalTime = 0;
		long targetProcessTime = 0;

		ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();
		for (RunningAppProcessInfo info : runningApps) {
			long time = getAppProcessTime(info.pid);
            totalTime += time;
            if (info.processName.contains(packageName)) {
            	targetProcessTime += time; //一个app可能有多个进程
            }
		}

		appCpuTime.put("totalTime", totalTime);
		appCpuTime.put("targetAppTime", targetProcessTime);

		return appCpuTime;
	}
	
	//返回进程消耗的cpu时间
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
		
		return utime + stime + cutime + cstime;
	}
	
	//将String转换成long
	private long string2Long(String s) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
		}
		return 0;
	}


	//获取从开机到执行时cpu在各个频率下运行的时间
	public long[][] getTimeInState() {
	    StringBuffer output = new StringBuffer();
	    output = excutor("cat /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
        String[] str = output.toString().split("\n");
        
	    long[][] timeInState;
	    timeInState = new long[str.length][2];
	    for (int i = 0; i < str.length; i++) {
	    	  timeInState[i][0]= string2Long(str[i].split(" ")[0]); //Ƶ��
	    	  timeInState[i][1] = string2Long(str[i].split(" ")[1]); //ʱ��
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
