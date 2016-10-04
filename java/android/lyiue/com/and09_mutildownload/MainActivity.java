package android.lyiue.com.and09_mutildownload;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 多线程断点续传
 */
public class MainActivity extends AppCompatActivity {
    private EditText et_url;
    private EditText et_threadCount;
    private EditText et_downpath;
    private LinearLayout ll_progress;
    private List<ProgressBar> progressBarList;

    //下载的参数
    private URL url;
    private int threadCount;
    private int runningThread;
    private String downPath;
    private String tempPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        et_url = (EditText) findViewById(R.id.et_url);
        et_threadCount = (EditText) findViewById(R.id.et_threadCount);
        et_downpath = (EditText) findViewById(R.id.et_downPath);
        ll_progress = (LinearLayout) findViewById(R.id.ll_progress);
        progressBarList = new ArrayList<ProgressBar>();
    }

    public void downLoad(View view) throws IOException {
        String urls = et_url.getText().toString();
        String threadCounts = et_threadCount.getText().toString();
        String downPaths = et_downpath.getText().toString();
        if (urls.trim().equals("") || threadCounts.trim().equals("") || downPaths.trim().equals("")) {
            Toast.makeText(MainActivity.this, "参数填写错误，请重新填写！", Toast.LENGTH_SHORT).show();
            return;
        }

        //为下载属性赋值
        this.url = new URL(urls);
        this.threadCount = Integer.parseInt(threadCounts);
        this.downPath = downPaths;
        //缓存目录
        this.tempPath = "sdcard/down/temp/";
        this.runningThread = this.threadCount;

        //添加进度条
        ll_progress.removeAllViews();
        progressBarList.clear();
        for (int i = 0; i < threadCount; i++) {
            LayoutInflater inflater = (LayoutInflater) MainActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.layout_progress, null);
            ll_progress.addView(v);
            //将进度条对象添加到list对象中
            ProgressBar progressBar = (ProgressBar) v.findViewById(R.id.pb_downProgess);
            progressBarList.add(progressBar);
        }
        //开始下载
        new Thread() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    int responseCode = connection.getResponseCode();

                    if (responseCode == 200) {
                        //获取服务器文件长度
                        int contentLength = connection.getContentLength();

                        File filePath = new File(handleDownPath());
                        if (!filePath.exists()) {
                            filePath.mkdirs();
                        }
                        Log.i("contentLength",contentLength+"");
                        //在本地创建服务器文件副本
                        RandomAccessFile randomAccessFile = new RandomAccessFile(getDownTitle(), "rw");
                        randomAccessFile.setLength(contentLength);
                        //计算每一个线程的下载开始位置和结束位置
                        int start;
                        int end;
                        int blockSize = contentLength / threadCount;

                        for (int i = 0; i < threadCount; i++) {
                            start = i * blockSize;
                            end = (i + 1) * blockSize - 1;
                            if (i == threadCount - 1) {
                                end = contentLength;
                            }
                            //开启线程
                            new DownLoadThread(i, start, end).start();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "获取服务器资源长度失败！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (IOException e) {
                    System.out.println("kkk..." + e.toString());
                }
            }

            /**
             * 处理本地保存路径
             * @return
             */
            private String handleDownPath() {
                int length = downPath.length();
                int index = downPath.lastIndexOf("/");
                if (index != length - 1) {
                    downPath = downPath + "/";
                }
                return downPath;
            }

            /**
             * 获取下载文件名
             * @return
             */
            private String getDownTitle() {
                String urls = url.toExternalForm();
                int lastIndex = urls.lastIndexOf("/");
                return handleDownPath() + urls.substring(lastIndex + 1);
            }

            class DownLoadThread extends Thread {
                private int threadID;
                private int startIndex;
                private int start;
                private int endIndex;

                public DownLoadThread(int threadID, int start, int end) {
                    this.threadID = threadID;
                    this.startIndex = start;
                    this.start = start;
                    this.endIndex = end;
                    progressBarList.get(threadID).setMax(endIndex - startIndex);
                }

                @Override
                public void run() {
                    HttpURLConnection connection;
                    try {
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(5000);

                        File temp = new File(getTempTitle(threadID));
                        if (temp.exists() && temp.length() != 0) {
                            BufferedReader bufferedReader = new BufferedReader(new FileReader(temp));
                            String line = bufferedReader.readLine();
                            if (line != null && !line.trim().equals("")) {
                                int last = Integer.parseInt(line);
                                progressBarList.get(threadID).setProgress(last - startIndex);
                                startIndex = last;
                            }
                        }

                        connection.addRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                        int responseCode = connection.getResponseCode();

                        if (responseCode == 206) {
                            InputStream inputStream = connection.getInputStream();

                            RandomAccessFile downFile = new RandomAccessFile(getDownTitle(), "rw");
                            downFile.seek(startIndex);
                            int len = -1;
                            byte[] buffer = new byte[1024 * 1024];
                            int total = 0;
                            //缓存文件
                            File tempProFile = new File(handleTempPath());
                            if (!tempProFile.exists()) {
                                tempProFile.mkdir();
                            }
                            while ((len = inputStream.read(buffer)) != -1) {
                                downFile.write(buffer, 0, len);
                                total += len;

                                //保存下载进度
                                RandomAccessFile tempFile = new RandomAccessFile(getTempTitle(threadID), "rwd");
                                int last = startIndex + total;
                                tempFile.write(String.valueOf(last).getBytes());
                                tempFile.close();

                                progressBarList.get(threadID).setProgress(last - start);
                            }
                            runOnUiThread(new Thread() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, threadID + "下载完成！", Toast.LENGTH_SHORT).show();
                                }
                            });
                            synchronized (MainActivity.class) {
                                runningThread--;
                                if (runningThread == 0) {
                                    for (int i = 0; i < threadCount; i++) {
                                        File delete = new File(getTempTitle(i));
                                        if (delete.exists()) {
                                            delete.delete();
                                        }
                                    }
                                    runOnUiThread(new Thread() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "所有资源下载完成！缓存文件已删除！", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }
                        } else {
                            Log.w("downfail", threadID + "下载失败!");
                            return;
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                /**
                 * 保证缓存路径最后一个字符是"/"
                 * @return
                 */
                private String handleTempPath() {
                    int length = tempPath.length();
                    int index = tempPath.lastIndexOf("/");
                    if (index != length - 1) {
                        tempPath += "/";
                    }
                    return tempPath;
                }

                /**
                 * 返回缓存名称,以txt保存
                 * @return
                 */
                private String getTempTitle(int id) {
                    String urls = url.toExternalForm();
                    int start = urls.lastIndexOf("/");
                    int end = urls.lastIndexOf(".");
                    return handleTempPath() + urls.substring(start + 1, end) + "_" + id + ".txt";
                }
            }
        }.start();
    }

}
