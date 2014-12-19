package com.mio.imageload.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by mio on 14-12-18.
 */
public class ImageLoader {
    private static ImageLoader mInstance;
    private final int mThreadCount;
    private String TAG = "ImageLoader";
    private Thread mBackThread;

    /**
     * 后台线程收到消息后,可以从任务池取任务
     */
    private Handler mBackThreadHander;
    private ExecutorService mThreadPool;

    /**
     * 信号量:方便控制当释放了某线程后,如何从线程队列取出任务
     */
    private Semaphore mThreadPoolSemaphore;

    /**
     * 信号量:当mBackThreadHander初始化结束后才放行
     */
    private Semaphore initSemaphore = new Semaphore (0);
    private LruCache<String, Bitmap> mLruCache;
    private LinkedList<Runnable> mTaskQueue;
    private Handler mUIHandler;
    private boolean isDiskCacheEnable = true; //open diskCache

    public ImageLoader(int threadCount) {
        mThreadCount = threadCount;
        init ();
    }


    /**
     * 最开始的初始化
     */
    private void init() {

        /** 初始化后台线程--->轮询取任务 **/
        initBackThread ();

        /** 初始化LruCache **/
        int maxMemory = (int) Runtime.getRuntime ().maxMemory ();
        int cacheMemory = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap> (cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes () * value.getHeight ();
            }
        };

        /** 创建线程池,存放加载图片任务 **/
        mThreadPool = Executors.newFixedThreadPool (mThreadCount);
        /** 任务队列 **/
        mTaskQueue = new LinkedList<Runnable> ();
        /** 方便控制当释放了某线程后,如何从任务队列取出任务,原来是随机的 **/
        mThreadPoolSemaphore = new Semaphore (mThreadCount);


    }

    /**
     * 初始化后台线程,当添加任务后取出任务
     */
    private void initBackThread() {

        mBackThread = new Thread () {

            @Override
            public void run() {
                Looper.prepare ();
                //阻塞的,当addTask后才会执行任务
                mBackThreadHander = new Handler () {
                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute (getTask ());
                        try {
                            mThreadPoolSemaphore.acquire ();
                        } catch (InterruptedException e) {
                            e.printStackTrace ();
                        }
                        initSemaphore.release ();
                    }
                };
                Looper.loop ();
            }
        };
        mBackThread.start ();

    }

    private Runnable getTask() {
        return mTaskQueue.removeLast ();
    }

    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add (runnable);
        try {
            if (mBackThreadHander == null)
                initSemaphore.acquire (); //阻塞等待initBackThread方法完成
        } catch (InterruptedException e) {
            e.printStackTrace ();
        }
        mBackThreadHander.sendEmptyMessage (0x11111);

    }


    /**
     * ~~~通过Path给ImageView设置方法~~~
     *
     * @param path
     * @param imageView
     * @param isFromNet
     */
    public void loadImage(String path, ImageView imageView, boolean isFromNet) {

        imageView.setTag (path);

        if (mUIHandler == null) {
            //refresh UI
            mUIHandler = new Handler () {
                @Override
                public void handleMessage(Message msg) {
                    ImageHolder holder = (ImageHolder) msg.obj;
                    Bitmap bitmap = holder.bitmap;
                    ImageView imageView = holder.imageView;
                    String path = holder.path;

                    if (imageView.getTag ().toString ().equals (path)) {
                        imageView.setImageBitmap (bitmap);
                    }
                }
            };

        }

        Bitmap bitmap = getBitmapFromLruCache (path); //get bitmap from lruCache

        if (bitmap != null) {
            Log.d (TAG, "读取内存缓存中图片.....");
            if (imageView.getTag ().toString ().equals (path)) {
                imageView.setImageBitmap (bitmap);
            }
            return;
        }

        addTask (buildTask (path, imageView, isFromNet)); //get bitmap from Disk/Net
    }


    /**
     * 刷新图片,调用UIHander
     *
     * @param path
     * @param imageView
     * @param bitmap
     */
    private void refreashBitmap(String path, ImageView imageView, Bitmap bitmap) {
        Message msg = Message.obtain ();
        ImageHolder holder = new ImageHolder ();
        holder.path = path;
        holder.imageView = imageView;
        holder.bitmap = bitmap;
        msg.obj = holder;
        mUIHandler.sendMessage (msg);
    }


    /**
     * **创建任务:如果内存有就不会进入到这里**
     *
     * @param path
     * @param imageView
     * @param isFromNet
     * @return
     */
    private Runnable buildTask(final String path, final ImageView imageView, final boolean isFromNet) {

        return new Runnable () {
            @Override
            public void run() {
                Bitmap bitmap = null;
                File file = getDiskCacheDir (imageView.getContext (), md5 (path));
                if (file.exists ()) {
                    //get image from disk
                    bitmap = ImageUtils.loadImageFromDisk (file.getAbsolutePath (), imageView);
                } else {

                    if (isDiskCacheEnable) {
                        // load  image from net to disk
                        if (ImageUtils.loadImageFromNet2Disk (path, file)) {
                            Log.d (TAG, " finish loadImageFromNet2Disk ");
                            //get image from disk
                            bitmap = ImageUtils.loadImageFromDisk (file.getAbsolutePath (), imageView);
                        }

                    } else {
                        // go directly to get image from net
                        Log.d (TAG, "go directly to get image from net");
                        bitmap = ImageUtils.loadImageFromNet (path, imageView);
                    }
                }

                // image to lruCache
                addBitmapToLruCache (path, bitmap);
                refreashBitmap (path, imageView, bitmap);
                mThreadPoolSemaphore.release ();
            }
        };
    }


    /**
     * 从内存缓存中获取图片
     *
     * @param path
     * @return
     */
    private Bitmap getBitmapFromLruCache(String path) {
        return mLruCache.get (path);
    }

    /**
     * 添加图片到内存
     *
     * @param path
     * @param bitmap
     */
    private void addBitmapToLruCache(String path, Bitmap bitmap) {
        if (getBitmapFromLruCache (path) == null) {
            if (bitmap != null) {
                mLruCache.put (path, bitmap);
            }
        }
    }


    /**
     * ~~获取实例~~
     * @param threadCount
     * @return
     */
    public static  ImageLoader getInstance(int threadCount) {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader (threadCount);
                }
            }
        }
        return mInstance;
    }

    /**
     * ~~获取实例~~
     * @return
     */
    public static ImageLoader getInstance() {
        getInstance (5);
        return mInstance;
    }

    class ImageHolder {
        private ImageView imageView;
        private String path;
        private Bitmap bitmap;
    }


    /**
     * 获取硬盘缓存地址
     *
     * @param context
     * @param fileName
     * @return
     */
    private File getDiskCacheDir(Context context, String fileName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals (Environment.getExternalStorageState ())) {
            cachePath = context.getExternalCacheDir ().getPath ();
        } else {
            cachePath = context.getCacheDir ().getPath ();
        }
        return new File (cachePath + File.separator + fileName);
    }



    /**
     * 利用签名辅助类，将字符串字节数组
     *
     * @param str
     * @return
     */
    public String md5(String str) {
        byte[] digest = null;
        try {
            MessageDigest md = MessageDigest.getInstance ("md5");
            digest = md.digest (str.getBytes ());
            return bytes2hex02 (digest);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace ();
        }
        return null;
    }

    /**
     * 将字节数组转成String
     *
     * @param bytes
     * @return
     */
    public String bytes2hex02(byte[] bytes) {
        StringBuilder sb = new StringBuilder ();
        String tmp = null;
        for(byte b : bytes) {
            // 将每个字节与0xFF进行与运算，然后转化为10进制，然后借助于Integer再转化为16进制
            tmp = Integer.toHexString (0xFF & b);
            if (tmp.length () == 1)// 每个字节8为，转为16进制标志，2个16进制位
            {
                tmp = "0" + tmp;
            }
            sb.append (tmp);
        }

        return sb.toString ();

    }

}
