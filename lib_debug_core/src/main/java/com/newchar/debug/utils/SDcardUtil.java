package com.newchar.debug.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import android.os.Environment;
import android.os.StatFs;

/**
 * 在AndroidManifest.xml中加入访问SDCard的权限如下: <!-- 在SDCard中创建与删除文件权限 -->
 * <uses-permission
 * android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS"/> <!--
 * 往SDCard写入数据权限 -->
 * <uses-permission
 * android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
 * 
 * 
 * 接着我们在使用SDcard进行读写的时候 会用到Environment类下面的几个静态方法：
 * 1:getDataDirectory()获取到Android中的data数据目录
 * 2:getDownloadCacheDirectory() 获取到下载的缓存目录
 * 3:getExternalStorageDirectory() 获取到外部存储的目录 一般指SDcard
 * 4:getExternalStorageState() 获取外部设置的当前状态 一般指SDcard, android系统中对于外部设置的状态，比较常用的是
 * MEDIA_MOUNTED（SDcard存在且可以进行读写） MEDIA_MOUNTED_READ_ONLY (SDcard存在，只可以进行读操作)
 * 当然还有其他的一些状态，可以在文档中进行查找到。
 * 5:getRootDirectory() 获取到Android Root路径
 * 6:isExternalStorageEmulated() 返回Boolean值判断外部设置是否有效
 * 7:isExternalStorageRemovable() 返回Boolean值，判断外部设置是否可以移除
 */
public class SDcardUtil {

	private final static String FILE_TYPE_MP3 = ".mp3";
	private final static String FILE_TYPE_JPG = ".jpg";
	private final static String FILE_TYPE_MP4 = ".mp4";
	private final static String FILE_TYPE_3GP = ".3gp";
	private final static String FILE_TYPE_GIF = ".gif";
	private final static String FILE_TYPE_PNG = ".png";
	private final static String FILE_TYPE_AVI = ".avi";

	private SDcardUtil() {
		throw new UnsupportedOperationException("cannot be instantiated");
	}

	/**
	 * 判断SDCard是否可用
	 * 
	 * @return
	 */
	public static boolean isSDCardEnable() {
		return Environment.MEDIA_MOUNTED.equals(
				Environment.getExternalStorageState());
	}

	/**
	 * 获取SD卡路径
	 * 
	 * @return
	 */
	public static String getSDCardPath() {
		return Environment.getExternalStorageDirectory().getAbsolutePath()
				+ File.separator;
	}

	/**
	 * 获取SD卡的剩余容量 单位byte
	 * 
	 * @return
	 */
	public static long getSDCardAllSize() {
		if (isSDCardEnable()) {
			StatFs stat = new StatFs(getSDCardPath());
			// 获取空闲的数据块的数量
			long availableBlocks = (long) stat.getAvailableBlocks() - 4;
			// 获取单个数据块的大小（byte）
			long freeBlocks = stat.getAvailableBlocks();
			return freeBlocks * availableBlocks;
		}
		return 0;
	}

	/**
	 * 获取指定路径所在空间的剩余可用容量字节数，单位byte
	 * 
	 * @param filePath
	 * @return 容量字节 SDCard可用空间，内部存储可用空间
	 */
	public static long getFreeBytes(String filePath) {
		// 如果是sd卡的下的路径，则获取sd卡可用容量
		if (filePath.startsWith(getSDCardPath())) {
			filePath = getSDCardPath();
		} else {// 如果是内部存储的路径，则获取内存存储的可用容量
			filePath = Environment.getDataDirectory().getAbsolutePath();
		}
		StatFs stat = new StatFs(filePath);
		long availableBlocks = (long) stat.getAvailableBlocks() - 4;
		return stat.getBlockSize() * availableBlocks;
	}

	/**
	 * 获取系统存储路径
	 * @return
	 */
	public static String getRootDirectoryPath() {
		return Environment.getRootDirectory().getAbsolutePath();
	}

	/**
	 * 存放在sdcard的根目录 结果false 为sd卡状态异常
	 */
	public boolean saveFileToSdcardRoot(String fileName, byte[] data) {
		boolean flag = false;
		/*
		 * 先判断sdcard的状态，是否存在
		 */
		String state = Environment.getExternalStorageState();
		FileOutputStream outputStream = null;
		File rootFile = Environment.getExternalStorageDirectory(); // 获得sdcard的根路径
		/*
		 * 表示sdcard挂载在手机上，并且可以读写 , sd卡准备就绪
		 */
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			File file = new File(rootFile, fileName);
			try {
				outputStream = new FileOutputStream(file);
				try {
					outputStream.write(data, 0, data.length);
					flag = true;
				} catch (IOException e) {
					flag = false;
					e.printStackTrace();
				}
			} catch (FileNotFoundException e) {
				flag = false;
				e.printStackTrace();
			} finally {
				if (outputStream != null) {
					try {
						outputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return flag;
	}

	/*
	 * 存放在sdcard下自定义的目录
	 */
	public boolean saveFileToSdcardDir(String fileName, byte[] data) {
		boolean flag = false;
		/*
		 * 先判断sdcard的状态，是否存在
		 */
		String state = Environment.getExternalStorageState();
		FileOutputStream outputStream = null;
		File rootFile = Environment.getExternalStorageDirectory(); // 获得sdcard的根路径
		/*
		 * 表示sdcard挂载在手机上，并且可以读写
		 */
		if (state.equals(Environment.MEDIA_MOUNTED)) {
			File file = new File(rootFile.getAbsoluteFile()
					+ File.pathSeparator + "应用名称" + "Cache");
			if (!file.exists()) {
				file.mkdirs();
			}
			try {
				outputStream = new FileOutputStream(new File(file, fileName));
				try {
					outputStream.write(data, 0, data.length);
					flag = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (outputStream != null) {
					try {
						outputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return flag;
	}

	/*
	 * 用于读取sdcard的数据
	 */
	public String readContextFromSdcard(String fileName) {

		String state = Environment.getExternalStorageState();
		File rootFile = Environment.getExternalStorageDirectory(); // 获得sdcard的目录

		FileInputStream inputStream = null;// 用于度取数据的流
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); // 用于存放独处的数据

		if (state.equals(Environment.MEDIA_MOUNTED)) {
			File file = new File(rootFile.getAbsoluteFile()
					+ File.pathSeparator + "应用名称" + "Cache");// 在sdcard目录下创建一个txt目录
			File file2 = new File(file, fileName);
			int len = 0;
			byte[] data = new byte[1024];
			if (file2.exists()) {
				try {
					inputStream = new FileInputStream(file2);
					try {
						while ((len = inputStream.read(data)) != -1) {
							outputStream.write(data, 0, data.length);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					return new String(outputStream.toByteArray());
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} finally {
					if (outputStream != null) {
						try {
							outputStream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * 对文件进行分类的保存到固定的文件中去
	 * 
	 * @param fileName
	 * @param data
	 */
	public void saveFileToSdcardBySuff(String fileName, byte[] data) {
		File file ;
		FileOutputStream outputStream = null;
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			/*
			 * 将不同的文件放入到不同的类别中
			 */
			if (fileName.endsWith(FILE_TYPE_MP3)) {
				file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
			} else if (fileName.endsWith(FILE_TYPE_JPG) || fileName.endsWith(FILE_TYPE_PNG) || fileName.endsWith(FILE_TYPE_GIF)) {
				file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
			} else if (fileName.endsWith(FILE_TYPE_MP4) || fileName.endsWith(FILE_TYPE_AVI) || fileName.endsWith(FILE_TYPE_3GP)) {
				file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
			} else {
				file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			}
			try {
				outputStream = new FileOutputStream(new File(file, fileName));
				outputStream.write(data, 0, data.length);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (outputStream != null) {
					try {
						outputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
						outputStream = null;
					}
				}
			}
		}
	}

	/*
	 * 删除一个文件
	 */
	public boolean deleteFileFromSdcard(String folder, String fileName) {
		boolean flag = false;
		File file = Environment.getExternalStorageDirectory();
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			File exitFile = new File(file.getAbsoluteFile()
					+ File.pathSeparator + folder, fileName);
			if (exitFile.exists()) {
				exitFile.delete();
			}
		}
		return flag;
	}

}