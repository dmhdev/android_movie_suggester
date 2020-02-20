package com.thestartupcoders.moviesuggesterai;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.parse.FunctionCallback;
import com.parse.ParseCloud;
import com.parse.ParseException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class MovieList extends Activity {
    /* access modifiers changed from: private */
    public String imagePath;
    /* access modifiers changed from: private */
    public ProgressDialog progressDialog;

    private class downloadURLShowDialog extends AsyncTask<String, Object, Object> {
        String chosenDescription;
        String chosenReleaseDate;
        String chosenTitle;
        String chosenUserRating;
        String[] chosenValues;
        String movieId;
        String posterPath;

        public downloadURLShowDialog(String posterPath2, String[] chosenValues2) {
            this.posterPath = posterPath2;
            this.chosenValues = chosenValues2;
            this.movieId = chosenValues2[0];
            this.chosenTitle = chosenValues2[1];
            this.chosenReleaseDate = chosenValues2[2];
            this.chosenDescription = chosenValues2[3];
            this.chosenUserRating = chosenValues2[4];
        }

        /* access modifiers changed from: protected */
        public Bitmap doInBackground(String... params) {
            return MovieList.this.getBitmapFromURL(this.posterPath, (String) MovieList.this.getLayoutInflater().inflate(C0292R.layout.movie_list, null).getTag());
        }

        /* access modifiers changed from: protected */
        public void onPostExecute(Object result) {
            MovieList.this.showMovieDetailsDialog(this.movieId, (Bitmap) result, this.chosenTitle, this.chosenReleaseDate, this.chosenDescription, this.chosenUserRating);
        }
    }

    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(C0292R.layout.movie_list);
        Intent i = getIntent();
        this.imagePath = i.getStringExtra("imagePath");
        ArrayList<String[]> relatedMovies = (ArrayList) i.getSerializableExtra("relatedMovies");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        createMovieList(relatedMovies);
        registerClickCallback();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        startActivityForResult(new Intent(getApplicationContext(), MainActivity.class), 0);
        return true;
    }

    public void createMovieList(ArrayList<String[]> relatedMovies) {
        ListView listViewMovies = (ListView) findViewById(C0292R.C0294id.listViewMovies);
        List<Map<String, String>> relatedMovieMaps = new ArrayList<>();
        Iterator it = relatedMovies.iterator();
        while (it.hasNext()) {
            String[] relatedMovie = (String[]) it.next();
            Map<String, String> relatedMovieMap = new HashMap<>();
            relatedMovieMap.put("movieId", relatedMovie[0]);
            relatedMovieMap.put("movieReleaseDate", relatedMovie[1]);
            relatedMovieMap.put("movieRating", relatedMovie[3]);
            relatedMovieMap.put("movieTitle", relatedMovie[2]);
            relatedMovieMaps.add(relatedMovieMap);
        }
        listViewMovies.setAdapter(new SimpleAdapter(this, relatedMovieMaps, C0292R.layout.movie_item, new String[]{"movieId", "movieReleaseDate", "movieRating", "movieTitle"}, new int[]{C0292R.C0294id.movieId, C0292R.C0294id.movieReleaseDate, C0292R.C0294id.movieRating, C0292R.C0294id.movieTitle}));
    }

    public void registerClickCallback() {
        ((ListView) findViewById(C0292R.C0294id.listViewMovies)).setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View viewClicked, int position, long id) {
                MovieList.this.progressDialog = ProgressDialog.show(MovieList.this, "", "Retrieving Movie Details...", true);
                MovieList.this.createMovieDialog(((TextView) viewClicked.findViewById(C0292R.C0294id.movieId)).getText().toString());
            }
        });
    }

    public void createMovieDialog(String movieId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("movieId", movieId);
        ParseCloud.callFunctionInBackground("getMovieDetails", params, new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                String chosenReleaseDate;
                if (e == null) {
                    try {
                        JSONObject verifyResponse = new JSONObject(result);
                        String movieId = verifyResponse.getString("id");
                        String chosenTitle = verifyResponse.getString("title");
                        String str = "NA";
                        try {
                            chosenReleaseDate = verifyResponse.getString("release_date").substring(0, 4);
                        } catch (Exception e2) {
                        }
                        String chosenDescription = verifyResponse.getString("overview");
                        String chosenUserRating = verifyResponse.getString("vote_average");
                        String posterPath = new StringBuilder(String.valueOf(MovieList.this.imagePath)).append(verifyResponse.getString("poster_path")).toString();
                        new downloadURLShowDialog(posterPath, new String[]{movieId, chosenTitle, chosenReleaseDate, chosenDescription, chosenUserRating}).execute(new String[]{posterPath});
                    } catch (NullPointerException e1) {
                        MovieList.this.progressDialog.dismiss();
                        Toast.makeText(MovieList.this, "There was an error retrieving Movie Info", 0).show();
                        e1.printStackTrace();
                    } catch (RuntimeException e22) {
                        MovieList.this.progressDialog.dismiss();
                        Toast.makeText(MovieList.this, "There was an error retrieving Movie Info", 0).show();
                        e22.printStackTrace();
                    } catch (JSONException e3) {
                        MovieList.this.progressDialog.dismiss();
                        Toast.makeText(MovieList.this, "There was an error retrieving Movie Info", 0).show();
                        e3.printStackTrace();
                    }
                } else {
                    MovieList.this.progressDialog.dismiss();
                    Toast.makeText(MovieList.this, "There was an error retrieving Movie Info", 0).show();
                    e.printStackTrace();
                }
            }
        });
    }

    public Bitmap getBitmapFromURL(String src, String layoutSetting) {
        if (layoutSetting.equals("large")) {
            src = src.replace("w92", "w300");
        } else if (layoutSetting.equals("xlarge")) {
            src = src.replace("w92", "w500");
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(src).openConnection();
            connection.setDoInput(true);
            connection.connect();
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return BitmapFactory.decodeResource(getResources(), C0292R.C0293drawable.logodialogplaceholder);
        }
    }

    public void showMovieDetailsDialog(String movieId, Bitmap posterBitmap, String chosenTitle, String chosenReleaseDate, String chosenDescription, String chosenUserRating) {
        final Dialog movieDetailsDialog = new Dialog(this);
        movieDetailsDialog.requestWindowFeature(1);
        movieDetailsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        movieDetailsDialog.setCanceledOnTouchOutside(true);
        movieDetailsDialog.setContentView(C0292R.layout.movie_dialog);
        ((ImageView) movieDetailsDialog.findViewById(C0292R.C0294id.chosenImage)).setImageBitmap(posterBitmap);
        TextView chosenDescriptionView = (TextView) movieDetailsDialog.findViewById(C0292R.C0294id.chosenDescription);
        TextView chosenRatingView = (TextView) movieDetailsDialog.findViewById(C0292R.C0294id.chosenRating);
        ((TextView) movieDetailsDialog.findViewById(C0292R.C0294id.chosenTitle)).setText(new StringBuilder(String.valueOf(chosenTitle)).append(" (").append(chosenReleaseDate).append(")").toString());
        if (chosenUserRating.equals("0")) {
            chosenRatingView.setText("Movie Rating: N/A");
        } else {
            chosenRatingView.setText("Movie Rating: " + chosenUserRating + "/10");
        }
        if (chosenDescription == null || chosenDescription.length() <= 1 || chosenDescription.equals("null") || chosenDescription.equals(null)) {
            chosenDescriptionView.setText("Plot summary unavailable.");
        } else {
            chosenDescriptionView.setText("\t" + chosenDescription);
        }
        ((Button) movieDetailsDialog.findViewById(C0292R.C0294id.closeButton)).setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                movieDetailsDialog.dismiss();
            }
        });
        movieDetailsDialog.show();
        this.progressDialog.dismiss();
    }
}
