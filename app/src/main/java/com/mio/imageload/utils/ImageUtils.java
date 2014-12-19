package com.mio.imageload.utils;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by mio on 14-12-18.
 */
public class ImageUtils {

    public static String TAG = "ImageUtils";

    /**
     * ~~获取ImageView控件的大小~~
     *
     * @param imageView
     * @return
     */
    public static ImageSize getImageViewSize(ImageView imageView) {

        ImageSize imageSize = new ImageSize ();
        DisplayMetrics displayMetrics = imageView.getContext ().getResources ().getDisplayMetrics ();

        ViewGroup.LayoutParams params = imageView.getLayoutParams ();

        int width = imageView.getWidth (); //控件的宽
        if (width <= 0) {
            width = params.width;           //在布局声明的宽
        }

        if (width <= 0) {
            width = getImageViewFieldValue (imageView, "mMaxWidth"); //反射拿到最大宽
        }
        if (width <= 0) {
            width = displayMetrics.widthPixels; //屏幕的宽
        }
        imageSize.width = width;

        int height = imageView.getWidth (); //控件的宽
        if (height <= 0) {
            height = params.height;           //在布局声明的宽
        }

        if (height <= 0) {
            height = getImageViewFieldValue (imageView, "mMaxHeight"); //反射拿到最大宽
        }
        if (height <= 0) {
            height = displayMetrics.heightPixels; //屏幕的宽
        }
        imageSize.height = height;


        return imageSize;
    }

    /**
     * 计算图片相对于ImageView控件的压缩比例
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int caculateZoomSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {

        //拿到图片的宽高
        int width = options.outWidth;
        int height = options.outHeight;

        //如果图片比ImageView小,不需要压缩了
        int widthZoomSize = 1;
        int heightZoomSize = 1;
        if (reqWidth > width) {
            widthZoomSize = Math.round (reqWidth * 1.0f / width);
        }

        if (reqHeight > height) {
            heightZoomSize = Math.round (reqHeight * 1.0f / height);
        }
        return Math.max (widthZoomSize, heightZoomSize);

    }

    /**
     * ~~~压缩图片~~~
     *
     * @param imagePath
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static Bitmap zoomBitmap(String imagePath, int reqWidth, int reqHeight) {
        BitmapFactory.Options op = new BitmapFactory.Options ();
        op.inJustDecodeBounds = true;
        BitmapFactory.decodeFile (imagePath, op);
        int zoomSize = caculateZoomSize (op, reqWidth, reqHeight);
        Log.d (TAG, "压缩比例:zoomSize:" + zoomSize);
        op.inSampleSize = zoomSize;
        op.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile (imagePath, op);
    }

    /**
     * @param urlStr 图片url
     * @param file   存储disk address
     * @return
     */
    public static boolean loadImageFromNet2Disk(String urlStr, File file) {
        Log.d (TAG, "从网络读取图片到disk,url:" + urlStr);
        FileOutputStream fos = null;
        InputStream is = null;

        try {
            URL url = new URL (urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection ();
            is = conn.getInputStream ();
            fos = new FileOutputStream (file);
            byte[] buf = new byte[512];
            int len = 0;
            while ((len = is.read (buf)) != -1) {
                fos.write (buf, 0, len);
            }
            fos.flush ();
            return true;

        } catch (Exception e) {
            Log.e (TAG, "loadImageFromNet2Disk--->error");
            e.printStackTrace ();
        } finally {
            try {
                if (is != null) {
                    is.close ();
                }
            } catch (IOException e) {
                Log.e (TAG, "loadImageFromNet2Disk--->IO error");
            }
            try {
                if (fos != null) {
                    fos.close ();
                }
            } catch (IOException e) {
                Log.e (TAG, "loadImageFromNet2Disk--->IO error");
            }
        }

        return false;
    }


    /**
     * ~~~根据url下载图片,自动压缩~~~
     *
     * @param urlStr
     * @param imageview
     * @return
     */
    public static Bitmap loadImageFromNet(String urlStr, ImageView imageview) {
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            URL url = new URL (urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection ();
            is = new BufferedInputStream (conn.getInputStream ());
            is.mark (is.available ());

            BitmapFactory.Options opts = new BitmapFactory.Options ();
            opts.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeStream (is, null, opts);

            //获取imageview想要显示的宽和高
            ImageSize imageViewSize = getImageViewSize (imageview);
            opts.inSampleSize = caculateZoomSize (opts,
                    imageViewSize.width, imageViewSize.height);

            opts.inJustDecodeBounds = false;
            is.reset ();
            bitmap = BitmapFactory.decodeStream (is, null, opts);

            conn.disconnect ();
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace ();
        } finally {
            try {
                if (is != null)
                    is.close ();
            } catch (IOException e) {
            }

            try {
                if (fos != null)
                    fos.close ();
            } catch (IOException e) {
            }
        }
        return null;
    }

    /**
     * ~~从本地读取图片~~
     *
     * @param path
     * @param imageView
     * @return
     */
    public static Bitmap loadImageFromDisk(String path, ImageView imageView) {
        Log.d (TAG, "读取硬盘中图片...path:" + path);
        Bitmap bitmap = null;
        ImageSize imageViewSize = getImageViewSize (imageView);
        return zoomBitmap (path, imageViewSize.width, imageViewSize.height);
    }


    /**
     * 通过反射获取imageview的某个属性值
     *
     * @return
     */
    private static int getImageViewFieldValue(Object obj, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField (fieldName);
            field.setAccessible (true);
            int fieldValue = field.getInt (obj);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }
        } catch (Exception e) {
        }
        return value;

    }

    private static class ImageSize {
        int width;
        int height;
    }
}
