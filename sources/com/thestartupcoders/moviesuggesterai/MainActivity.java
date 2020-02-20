package com.thestartupcoders.moviesuggesterai;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import com.parse.FunctionCallback;
import com.parse.ParseCloud;
import com.parse.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    /* access modifiers changed from: private */
    public String imagePath;
    private EditText movieSearchInput;
    /* access modifiers changed from: private */
    public ProgressDialog progressDialog;

    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(C0292R.layout.activity_main);
        setTMDBImagePath();
        setSearchListener();
    }

    public void onBackPressed() {
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(C0292R.menu.main, menu);
        return true;
    }

    public void setTMDBImagePath() {
        ParseCloud.callFunctionInBackground("getImagePath", new HashMap<>(), new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                if (e == null) {
                    try {
                        MainActivity.this.imagePath = new StringBuilder(String.valueOf(result)).append("w92").toString();
                    } catch (NullPointerException e1) {
                        MainActivity.this.imagePath = " http://d3gtl9l2a4fn1j.cloudfront.net/t/p/w92";
                        e1.printStackTrace();
                    } catch (RuntimeException e2) {
                        MainActivity.this.imagePath = " http://d3gtl9l2a4fn1j.cloudfront.net/t/p/w92";
                        e2.printStackTrace();
                    }
                } else {
                    MainActivity.this.imagePath = " http://d3gtl9l2a4fn1j.cloudfront.net/t/p/w92";
                    e.printStackTrace();
                }
            }
        });
    }

    public void setSearchListener() {
        this.movieSearchInput = (EditText) findViewById(C0292R.C0294id.movieSearchInput);
        this.movieSearchInput.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != 3) {
                    return false;
                }
                MainActivity.this.movieSearch(MainActivity.this.findViewById(C0292R.layout.activity_main));
                return true;
            }
        });
    }

    public void movieSearch(View v) {
        this.movieSearchInput = (EditText) findViewById(C0292R.C0294id.movieSearchInput);
        ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(this.movieSearchInput.getWindowToken(), 0);
        this.progressDialog = ProgressDialog.show(this, "", "Searching Similar Movies...", true);
        String searchTerm = ((EditText) findViewById(C0292R.C0294id.movieSearchInput)).getText().toString().replaceAll(" ", "+");
        HashMap<String, Object> params = new HashMap<>();
        params.put("searchTerm", searchTerm);
        ParseCloud.callFunctionInBackground("findMovieId", params, new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                if (e == null) {
                    String str = "";
                    try {
                        MainActivity.this.parseSimilarMovies(result);
                    } catch (NullPointerException e1) {
                        e1.printStackTrace();
                    } catch (RuntimeException e2) {
                        e2.printStackTrace();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "No Movies found based on Input", 0).show();
                    MainActivity.this.progressDialog.dismiss();
                    e.printStackTrace();
                }
            }
        });
    }

    public void parseSimilarMovies(String movieId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("movieId", movieId);
        ParseCloud.callFunctionInBackground("getSimilarMovies", params, new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                String movieReleaseDate;
                if (e == null) {
                    try {
                        ArrayList<String[]> relatedMovies = new ArrayList<>();
                        JSONArray similarMovies = new JSONArray(result);
                        for (int i = 0; i < similarMovies.length(); i++) {
                            JSONObject similarMovie = similarMovies.getJSONObject(i);
                            String movieId = similarMovie.getString("id");
                            String str = "NA";
                            try {
                                movieReleaseDate = similarMovie.getString("release_date").substring(0, 4);
                            } catch (Exception e2) {
                            }
                            String movieTitle = similarMovie.getString("title");
                            String movieRating = similarMovie.getString("vote_average");
                            if (movieRating.equals("0")) {
                                movieRating = "NA";
                            }
                            if (movieRating.length() == 1) {
                                movieRating = new StringBuilder(String.valueOf(movieRating)).append(".0").toString();
                            }
                            relatedMovies.add(new String[]{movieId, movieReleaseDate, movieTitle, movieRating});
                        }
                        MainActivity.this.progressDialog.dismiss();
                        MainActivity.this.openMovieList(relatedMovies);
                    } catch (NullPointerException e1) {
                        e1.printStackTrace();
                    } catch (RuntimeException e22) {
                        e22.printStackTrace();
                    } catch (JSONException e12) {
                        e12.printStackTrace();
                    }
                } else {
                    MainActivity.this.progressDialog.dismiss();
                    e.printStackTrace();
                }
            }
        });
    }

    public void openMovieList(ArrayList<String[]> relatedMovies) {
        Intent openDriverList = new Intent("com.thestartupcoders.moviesuggesterai.MOVIELIST");
        openDriverList.putExtra("imagePath", this.imagePath);
        openDriverList.putExtra("relatedMovies", relatedMovies);
        startActivity(openDriverList);
    }

    public void openAbout(View v) {
        startActivity(new Intent("com.thestartupcoders.moviesuggesterai.ABOUT"));
    }
}
