package com.oldenweb.Monsters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.example.games.basegameutils.BaseGameActivity;

public class Main extends BaseGameActivity {
	Handler h = new Handler();
	SharedPreferences sp;
	Editor ed;
	boolean isForeground = true;
	MediaPlayer mp;
	SoundPool sndpool;
	int snd_info;
	int snd_hit;
	int snd_result;
	int snd_move;
	int score;
	int t;
	int screen_width;
	int screen_height;
	int current_section = R.id.main;
	boolean show_leaderboard;
	final List<ImageView> holes = new ArrayList<ImageView>();
	float start_x;
	float start_y;
	int monster_size;
	AnimatorSet anim;
	int current_monster;
	final int time = 60; // time
	final int cols = 6; // number of cols
	final int rows = 3; // number of rows
	final int show_time = 350; // monster show time in milliseconds

	// AdMob
	AdView adMob_smart;
	InterstitialAd adMob_interstitial;
	final boolean show_admob_smart = true; // show AdMob Smart banner
	final boolean show_admob_interstitial = true; // show AdMob Interstitial

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// fullscreen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// preferences
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		ed = sp.edit();

		// AdMob smart
		add_admob_smart();

		// bg sound
		mp = new MediaPlayer();
		try {
			AssetFileDescriptor descriptor = getAssets().openFd("snd_bg.mp3");
			mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
			descriptor.close();
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mp.setLooping(true);
			mp.setVolume(0, 0);
			mp.prepare();
			mp.start();
		} catch (Exception e) {
		}

		// if mute
		if (sp.getBoolean("mute", false)) {
			((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
		} else {
			mp.setVolume(0.2f, 0.2f);
		}

		// SoundPool
		sndpool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
		try {
			snd_result = sndpool.load(getAssets().openFd("snd_result.mp3"), 1);
			snd_info = sndpool.load(getAssets().openFd("snd_info.mp3"), 1);
			snd_hit = sndpool.load(getAssets().openFd("snd_hit.mp3"), 1);
			snd_move = sndpool.load(getAssets().openFd("snd_move.mp3"), 1);
		} catch (IOException e) {
		}

		// hide navigation bar listener
		findViewById(R.id.all).setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				hide_navigation_bar();
			}
		});

		// add holes
		for (int i = 0; i < cols * rows; i++) {
			ImageView hole = new ImageView(this);
			hole.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			hole.setImageResource(R.drawable.hole);
			((ViewGroup) findViewById(R.id.game)).addView(hole);
			holes.add(hole);
		}

		// index
		findViewById(R.id.txt_score).bringToFront();
		findViewById(R.id.txt_time).bringToFront();
		findViewById(R.id.monster).bringToFront();
		findViewById(R.id.mess).bringToFront();

		// custom font
		Typeface font = Typeface.createFromAsset(getAssets(), "CooperBlack.otf");
		((TextView) findViewById(R.id.txt_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_high_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_score)).setTypeface(font);
		((TextView) findViewById(R.id.txt_time)).setTypeface(font);
		((TextView) findViewById(R.id.mess)).setTypeface(font);

		SCALE();

		// touch listener
		findViewById(R.id.monster).setOnTouchListener(new OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (v.isEnabled() && event.getAction() == MotionEvent.ACTION_DOWN) {
					v.setEnabled(false);
					h.removeCallbacks(show_monster);
					h.removeCallbacks(hide_monster);

					// score
					score += 10;
					((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);

					// sound
					if (!sp.getBoolean("mute", false) && isForeground)
						sndpool.play(snd_hit, 1f, 1f, 0, 0, 1);

					// animation
					if (anim != null) {
						anim.removeAllListeners();
						anim.cancel();
					}

					h.post(hide_monster);
				}

				return false;
			}
		});
	}

	// SCALE
	void SCALE() {
		// txt_score
		((TextView) findViewById(R.id.txt_score)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) findViewById(R.id.txt_score).getLayoutParams();
		l.setMargins((int) DpToPx(6), (int) DpToPx(2), 0, 0);
		findViewById(R.id.txt_score).setLayoutParams(l);

		// txt_time
		((TextView) findViewById(R.id.txt_time)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		l = (FrameLayout.LayoutParams) findViewById(R.id.txt_time).getLayoutParams();
		l.setMargins(0, (int) DpToPx(2), (int) DpToPx(6), 0);
		findViewById(R.id.txt_time).setLayoutParams(l);

		// buttons text
		((TextView) findViewById(R.id.btn_sign)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		((TextView) findViewById(R.id.btn_leaderboard)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		((TextView) findViewById(R.id.btn_sound)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		((TextView) findViewById(R.id.btn_start)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(30));
		((TextView) findViewById(R.id.btn_exit)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		((TextView) findViewById(R.id.btn_home)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(24));
		((TextView) findViewById(R.id.btn_start2)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(24));

		// text result
		((TextView) findViewById(R.id.txt_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(60));
		((TextView) findViewById(R.id.txt_high_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(30));

		// text mess
		((TextView) findViewById(R.id.mess)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(30));
	}

	// START
	void START() {
		score = 0;
		t = time;
		show_section(R.id.game);
		findViewById(R.id.mess).setVisibility(View.GONE);
		findViewById(R.id.monster).setEnabled(false);
		findViewById(R.id.monster).setScaleX(0f);
		findViewById(R.id.monster).setScaleY(0f);

		((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);
		((TextView) findViewById(R.id.txt_time)).setText(getString(R.string.time) + " " + t);

		// screen size
		screen_width = Math.max(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());
		screen_height = Math.min(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());

		// monster_size
		monster_size = Math.min(screen_width / cols, screen_height / rows);
		findViewById(R.id.monster).getLayoutParams().width = findViewById(R.id.monster).getLayoutParams().height = monster_size;

		start_x = (screen_width - monster_size * cols) / 2f;
		start_y = (screen_height - monster_size * rows) / 2f;

		// holes position
		int x_pos = 0;
		int y_pos = 0;
		for (int i = 0; i < holes.size(); i++) {
			holes.get(i).getLayoutParams().width = holes.get(i).getLayoutParams().height = monster_size;
			holes.get(i).setX(start_x + x_pos * monster_size);
			holes.get(i).setY(start_y + y_pos * monster_size);
			x_pos++;

			if (x_pos == cols) {
				x_pos = 0;
				y_pos++;
			}
		}

		h.postDelayed(TIMER, 1000);
		h.postDelayed(show_monster, 1 + Math.round(Math.random() * 500));
	}

	// show_monster
	Runnable show_monster = new Runnable() {
		@Override
		public void run() {
			// random_monster
			int random_monster = (int) Math.round(Math.random() * (holes.size() - 1));
			findViewById(R.id.monster).setX(holes.get(random_monster).getX());
			findViewById(R.id.monster).setY(holes.get(random_monster).getY());

			// animate
			anim = new AnimatorSet();
			anim.playTogether(ObjectAnimator.ofFloat(findViewById(R.id.monster), "scaleX", 1f),
					ObjectAnimator.ofFloat(findViewById(R.id.monster), "scaleY", 1f));
			anim.setDuration(100);
			anim.addListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {
					// sound
					if (!sp.getBoolean("mute", false) && isForeground)
						sndpool.play(snd_move, 0.2f, 0.2f, 0, 0, 1);
				}

				@Override
				public void onAnimationRepeat(Animator animation) {
				}

				@Override
				public void onAnimationCancel(Animator animation) {
				}

				@Override
				public void onAnimationEnd(Animator animation) {
					findViewById(R.id.monster).setEnabled(true);
					h.postDelayed(hide_monster, show_time);
				}
			});
			anim.start();
		}
	};

	// hide_monster
	Runnable hide_monster = new Runnable() {
		@Override
		public void run() {
			// animate
			anim = new AnimatorSet();
			anim.playTogether(ObjectAnimator.ofFloat(findViewById(R.id.monster), "scaleX", 0f),
					ObjectAnimator.ofFloat(findViewById(R.id.monster), "scaleY", 0f));
			anim.setDuration(100);
			anim.addListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {
					findViewById(R.id.monster).setEnabled(false);
				}

				@Override
				public void onAnimationRepeat(Animator animation) {
				}

				@Override
				public void onAnimationCancel(Animator animation) {
				}

				@Override
				public void onAnimationEnd(Animator animation) {
					h.postDelayed(show_monster, 1 + Math.round(Math.random() * 500));
				}
			});
			anim.start();
		}
	};

	// TIMER
	Runnable TIMER = new Runnable() {
		@Override
		public void run() {
			t--;
			((TextView) findViewById(R.id.txt_time)).setText(getString(R.string.time) + " " + t);

			// complete
			if (t == 0) {
				// animation
				if (anim != null) {
					anim.end();
					anim.removeAllListeners();
					anim.cancel();
				}
				h.removeCallbacks(show_monster);
				h.removeCallbacks(hide_monster);

				// sound
				if (!sp.getBoolean("mute", false) && isForeground)
					sndpool.play(snd_info, 1f, 1f, 0, 0, 1);

				findViewById(R.id.mess).setVisibility(View.VISIBLE);
				findViewById(R.id.monster).setEnabled(false);
				h.postDelayed(STOP, 3000);
				return;
			}

			h.postDelayed(TIMER, 1000);
		}
	};

	// STOP
	Runnable STOP = new Runnable() {
		@Override
		public void run() {
			// show result
			show_section(R.id.result);

			// save score
			if (score > sp.getInt("score", 0)) {
				ed.putInt("score", score);
				ed.commit();
			}

			// show score
			((TextView) findViewById(R.id.txt_result)).setText(getString(R.string.score) + " " + score);
			((TextView) findViewById(R.id.txt_high_result)).setText(getString(R.string.high_score) + " " + sp.getInt("score", 0));

			// sound
			if (!sp.getBoolean("mute", false) && isForeground)
				sndpool.play(snd_result, 1f, 1f, 0, 0, 1);

			// save score to leaderboard
			if (getApiClient().isConnected()) {
				Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));
			}

			// AdMob Interstitial
			add_admob_interstitial();
		}
	};

	// onClick
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_start:
		case R.id.btn_start2:
			START();
			break;
		case R.id.btn_home:
			show_section(R.id.main);
			break;
		case R.id.btn_exit:
			finish();
			break;
		case R.id.btn_sound:
			if (sp.getBoolean("mute", false)) {
				ed.putBoolean("mute", false);
				mp.setVolume(0.2f, 0.2f);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_mute));
			} else {
				ed.putBoolean("mute", true);
				mp.setVolume(0, 0);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
			}
			ed.commit();
			break;
		case R.id.btn_leaderboard:
			// show leaderboard
			show_leaderboard = true;
			if (getApiClient().isConnected())
				onSignInSucceeded();
			else
				beginUserInitiatedSignIn();
			break;
		case R.id.btn_sign:
			// Google sign in/out
			if (getApiClient().isConnected()) {
				signOut();
				onSignInFailed();
			} else
				beginUserInitiatedSignIn();
			break;
		}
	}

	@Override
	public void onBackPressed() {
		switch (current_section) {
		case R.id.main:
			super.onBackPressed();
			break;
		case R.id.result:
			show_section(R.id.main);
			break;
		case R.id.game:
			show_section(R.id.main);
			h.removeCallbacks(STOP);
			h.removeCallbacks(TIMER);
			h.removeCallbacks(show_monster);
			h.removeCallbacks(hide_monster);

			// animation
			if (anim != null) {
				anim.removeAllListeners();
				anim.cancel();
			}
			break;
		}
	}

	// show_section
	void show_section(int section) {
		current_section = section;
		findViewById(R.id.main).setVisibility(View.GONE);
		findViewById(R.id.game).setVisibility(View.GONE);
		findViewById(R.id.result).setVisibility(View.GONE);
		findViewById(current_section).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		h.removeCallbacks(STOP);
		h.removeCallbacks(TIMER);
		h.removeCallbacks(show_monster);
		h.removeCallbacks(hide_monster);
		mp.release();
		sndpool.release();

		// animation
		if (anim != null) {
			anim.removeAllListeners();
			anim.cancel();
		}

		// destroy AdMob
		if (adMob_smart != null)
			adMob_smart.destroy();

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		isForeground = false;
		mp.setVolume(0, 0);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		isForeground = true;

		if (!sp.getBoolean("mute", false) && isForeground)
			mp.setVolume(0.2f, 0.2f);
	}

	// DpToPx
	float DpToPx(float dp) {
		return (dp * Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 540f);
	}

	// hide_navigation_bar
	@TargetApi(Build.VERSION_CODES.KITKAT)
	void hide_navigation_bar() {
		// fullscreen mode
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hide_navigation_bar();
		}
	}

	@Override
	public void onSignInSucceeded() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_out));

		// save score to leaderboard
		if (show_leaderboard) {
			if (sp.contains("score"))
				Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));

			// show leaderboard
			startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), getString(R.string.leaderboard_id)),
					9999);
		}

		// get score from leaderboard
		Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), getString(R.string.leaderboard_id),
				LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(
				new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
					@Override
					public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
						if (scoreResult != null && scoreResult.getStatus().getStatusCode() == GamesStatusCodes.STATUS_OK
								&& scoreResult.getScore() != null) {
							// save score localy
							if ((int) scoreResult.getScore().getRawScore() > sp.getInt("score", 0)) {
								ed.putInt("score", (int) scoreResult.getScore().getRawScore());
								ed.commit();
							}
						}
					}
				});

		show_leaderboard = false;
	}

	@Override
	public void onSignInFailed() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_in));
		show_leaderboard = false;
	}

	// add_admob_smart
	void add_admob_smart() {
		if (show_admob_smart
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_smart = new AdView(this);
			adMob_smart.setAdUnitId(getString(R.string.adMob_smart));
			adMob_smart.setAdSize(AdSize.SMART_BANNER);
			((ViewGroup) findViewById(R.id.admob)).addView(adMob_smart);
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_smart.loadAd(builder.build());
		}
	}

	// add_admob_interstitial
	void add_admob_interstitial() {
		if (show_admob_interstitial
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_interstitial = new InterstitialAd(this);
			adMob_interstitial.setAdUnitId(getString(R.string.adMob_interstitial));
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_interstitial.setAdListener(new AdListener() {
				@Override
				public void onAdLoaded() {
					super.onAdLoaded();

					if (current_section != R.id.game)
						adMob_interstitial.show();
				}
			});
			adMob_interstitial.loadAd(builder.build());
		}
	}
}