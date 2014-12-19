package com.mio.imageload;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.mio.imageload.utils.ImageLoader;


public class MainActivity extends ActionBarActivity {

    private String[] urls = Images.imageThumbUrls;
    private ImageLoader imageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main);
        imageLoader = ImageLoader.getInstance ();
        GridView gridView = (GridView) findViewById (R.id.gv);
        gridView.setAdapter (new MyAdapter ());
    }

    class MyAdapter extends BaseAdapter{

        @Override
        public int getCount() {
            return urls.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null ){
                convertView = MainActivity.this.getLayoutInflater ().inflate(
                        R.layout.item, parent, false);
            }

            ImageView iv = (ImageView) convertView.findViewById (R.id.iv);
            iv.setImageResource (R.drawable.ic_launcher);
            imageLoader.loadImage (urls[position],iv,true);
            return convertView;

        }
    }

}
