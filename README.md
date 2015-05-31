# 说明：
对比[测试APP](http://app.bilibili.cn/)弹幕硬解和软解的耗电量。  
主要使用 CPU ，所以只考虑 CPU 的耗电。  
因为耗时较长，用 UIautomator 自动修改设置和播放。  
需要 Root。  

# 数据来源：
**/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state**  
记录了 CPU 从开机到读取文件时，在各个频率下的运行时间，单位：10 mS  
[说明](https://www.kernel.org/doc/Documentation/cpu-freq/cpufreq-stats.txt)  


**PowerProfile**  
获取 CPU 在各个频率下运行时的平均电流，单位 mA。  
[说明](https://source.android.com/devices/tech/power/index.html)   
*第三方 ROM 一般是错的。*  

**/proc/[pid]/stat**
进程已运行时间 utime + stime + cutime + cstime，pid 是进程号(adb shell ps)。  
[说明](https://www.kernel.org/doc/Documentation/filesystems/proc.txt)  

**ActivityManager**
通过 PackageName 获得 APP 下的进程号。  
[说明](http://developer.android.com/reference/android/app/ActivityManager.html)  

# 测试场景：
准备工作：离线下载好待测视频，开飞行模式，清理后台。  
1、记录 APP 已经运行的时间 targetAppTimeBefor ，所有 APP 已经运行时间 totalTimeBefor ，CPU 在各个频率的运行时间 timeInStateBefor[1] 、timeInStateBefor[2] 、……  
2、播放有弹幕的视频。  
3、结束播放，记录 APP 已经运行的时间 targetAppTimeAfter ，所有 APP 已经运行时间totalTimeAfter，CPU 在各个频率的运行时间 timeInStateAfter[1] 、 timeInStateAfter[2] 、……  
4、读取 CPU 在各个频率下运行时的平均电流 powerUsedOnDiffSpeeds[1] 、powerUsedOnDiffSpeeds[2] 、……  

# 计算公式：
**目标 APP 占总 CPU 时间的比例：**  
ratio = (targetAppTimeAfter - targetAppTimeBefor) / (totalTimeAfter - totalTimeBefor)  

**CPU 总耗电：**  
totalPower  = ((timeInStateAfter[1] - timeInStateBefor[1]) / 100 * powerUsedOnDiffSpeeds[1]) +  ((timeInStateAfter[2] - timeInStateBefor[2]) / 100 * powerUsedOnDiffSpeeds[2]) + ……  

**目标 APP 的 CPU 耗电（单位：mAh）：**  
appCpuTotalPower  = cpuTotalPower * ratio / 3600  

![](http://ww2.sinaimg.cn/mw690/4835282dgw1esnful69lxj20a00b0q3m.jpg)
