package com.fanfou.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.fanfou.app.api.Status;
import com.fanfou.app.cache.ImageLoaderInterface;
import com.fanfou.app.cache.ImageLoaderListener;
import com.fanfou.app.config.Commons;
import com.fanfou.app.service.ActionService;
import com.fanfou.app.ui.ActionBar;
import com.fanfou.app.ui.ActionBar.Action;
import com.fanfou.app.ui.ActionManager;
import com.fanfou.app.util.DateTimeHelper;
import com.fanfou.app.util.IOHelper;
import com.fanfou.app.util.OptionHelper;
import com.fanfou.app.util.StatusHelper;
import com.fanfou.app.util.StringHelper;
import com.fanfou.app.util.Utils;

/**
 * @author mcxiaoke
 * 
 */
public class StatusPage extends BaseActivity implements OnClickListener,
		ActionManager.ResultListener {

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private static final String tag = StatusPage.class.getSimpleName();

	private void log(String message) {
		Log.e(tag, message);
	}

	private ActionBar mActionBar;

	private ImageLoaderInterface imageLoader = null;

	private Status status;
	private Status thread;

	private View vUser;

	private ImageView iUserHead;
	// private TextView tUserId;
	private TextView tUserName;

	private ImageView iShowUser;

	private TextView tContent;
	private ImageView iPhoto;

	private TextView tDate;
	private TextView tSource;

	private ImageView bReply;
	private ImageView bRepost;
	private ImageView bFavorite;
	private ImageView bShare;

	private View vThread;
	private TextView tThreadName;
	private TextView tThreadText;

	private Handler mHandler;

	private boolean isMe;

	private int photoLevel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		parseIntent();

		imageLoader = App.me.imageLoader;
		mHandler = new Handler();

		setContentView(R.layout.status);
		setActionBar();

		vUser = findViewById(R.id.status_top);
		vUser.setOnClickListener(this);

		iUserHead = (ImageView) findViewById(R.id.user_head);
		imageLoader.set(status.userProfileImageUrl, iUserHead,R.drawable.default_head);

		tUserName = (TextView) findViewById(R.id.user_name);
		tUserName.setText(status.userScreenName);
		TextPaint tp = tUserName.getPaint();
		tp.setFakeBoldText(true);

		iShowUser = (ImageView) findViewById(R.id.status_action_user);

		tContent = (TextView) findViewById(R.id.status_text);
		StatusHelper.setStatus(tContent, status.text);

		iPhoto = (ImageView) findViewById(R.id.status_photo);

		if (!StringHelper.isEmpty(status.photoLargeUrl)) {
			photoLevel = OptionHelper.parseInt(this, R.string.option_pic_level);
			if (photoLevel < 0) {
				photoLevel = 0;
			}
			if (App.DEBUG)
				log("level=" + photoLevel);
			if (photoLevel == 2) {
				imageLoader.set(status.photoLargeUrl, iPhoto);
			} else if (photoLevel == 1) {
				imageLoader.set(status.photoThumbUrl, iPhoto);
				iPhoto.setOnClickListener(this);
			} else if (photoLevel == 0) {
				iPhoto.setImageResource(R.drawable.photo_icon);
				iPhoto.setOnClickListener(this);
				// iPhoto.setVisibility(View.GONE);
			}
		} else {
			iPhoto.setVisibility(View.GONE);
		}

		tDate = (TextView) findViewById(R.id.status_date);
		tDate.setText("时间：" + DateTimeHelper.getInterval(status.createdAt));

		tSource = (TextView) findViewById(R.id.status_source);
		tSource.setText("来源：" + status.source);

		vThread = findViewById(R.id.status_thread);
		tThreadName = (TextView) findViewById(R.id.status_thread_user);
		TextPaint tp2 = tThreadName.getPaint();
		tp2.setFakeBoldText(true);
		tThreadText = (TextView) findViewById(R.id.status_thread_text);
		vThread.setVisibility(View.GONE);

		bReply = (ImageView) findViewById(R.id.status_action_reply);
		if (isMe) {
			bReply.setImageResource(R.drawable.i_bar2_delete);
		} else {
			bReply.setImageResource(R.drawable.i_bar2_reply);
		}

		bRepost = (ImageView) findViewById(R.id.status_action_retweet);
		bFavorite = (ImageView) findViewById(R.id.status_action_favorite);
		bShare = (ImageView) findViewById(R.id.status_action_share);

		bReply.setOnClickListener(this);
		bRepost.setOnClickListener(this);
		bFavorite.setOnClickListener(this);
		bShare.setOnClickListener(this);

		tContent.setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				doCopy(StatusHelper.getSimpifiedText(tContent.getText()
						.toString()));
				return true;
			}
		});

		updateFavoriteButton();

		doFetchThread();

	}

	private void updateFavoriteButton() {
		if (status.favorited) {
			bFavorite.setImageResource(R.drawable.i_bar2_unfavorite);
		} else {
			bFavorite.setImageResource(R.drawable.i_bar2_favorite);
		}
	}

	private void parseIntent() {
		Intent intent = getIntent();
		status = (Status) intent.getSerializableExtra(Commons.EXTRA_STATUS);

		isMe = status.userId.equals(App.me.userId);
	}

	/**
	 * 初始化和设置ActionBar
	 */
	private void setActionBar() {
		mActionBar = (ActionBar) findViewById(R.id.actionbar);
		mActionBar.setTitle("消息");

		Intent intent = new Intent(this, WritePage.class);
		intent.putExtra(Commons.EXTRA_TYPE, WritePage.TYPE_REPLY);
		intent.putExtra(Commons.EXTRA_STATUS, status);
		Action action = new ActionBar.IntentAction(mContext, intent,
				R.drawable.i_write);
		mActionBar.setRightAction(action);
		mActionBar.setLeftAction(new ActionBar.BackAction(mContext));
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.status_action_reply:
			if (isMe) {
				doDelete();
			} else {
				ActionManager.doReply(this, status);
			}
			break;
		case R.id.status_action_retweet:
			ActionManager.doRetweet(this, status);
			break;
		case R.id.status_action_favorite:
			doFavorite();
			break;
		case R.id.status_action_share:
			ActionManager.doShare(this, status);
			break;
		case R.id.status_top:
			ActionManager.doProfile(this, status);
			break;
		// case R.id.status_text:
		// break;
		case R.id.status_photo:
			doShowBigPicture();
			// Utils.goPhotoViewPage(this, null);
			break;
		default:
			break;
		}
	}

	private void doShowBigPicture() {
		ImageLoaderListener callback = new ImageLoaderListener() {

			@Override
			public void onFinish(String key) {
				iPhoto.setOnClickListener(null);
				Bitmap bitmap=App.me.imageLoader.load(key);
				if(bitmap!=null){
					iPhoto.setImageBitmap(bitmap);
				}
				iPhoto.postInvalidate();
			}

			@Override
			public void onError(String message) {
				if (App.DEBUG) {
					log("onError message=" + message);
				}
				iPhoto.setOnClickListener(StatusPage.this);
			}
		};
		iPhoto.setImageResource(R.drawable.photo_loading);
		Bitmap bitmap = App.me.imageLoader.load(status.photoLargeUrl,
				callback);
		if (bitmap != null) {
			iPhoto.setImageBitmap(bitmap);
			iPhoto.setOnClickListener(null);
		}
	}

	private void doDelete() {
		ActionManager.doDelete(this, status.id, true);
	}

	private void doFavorite() {
		ActionManager.ResultListener li = new ActionManager.ResultListener() {

			@Override
			public void onActionSuccess(int type, String message) {
				if (App.DEBUG)
					log("type="
							+ (type == Commons.ACTION_STATUS_FAVORITE ? "收藏"
									: "取消收藏") + " message=" + message);
				if (type == Commons.ACTION_STATUS_FAVORITE) {
					status.favorited = true;
				} else {
					status.favorited = false;
				}
				updateFavoriteButton();
			}

			@Override
			public void onActionFailed(int type, String message) {
			}
		};
		ActionManager.doFavorite(this, status, li);
	}

	private void doCopy(String content) {
		IOHelper.copyToClipBoard(this, content);
		Utils.notify(this, "消息内容已复制到剪贴板");
	}

	private void showThread(Status result) {
		if (App.DEBUG)
			log("showThread() status.text=" + result.text);
		thread = result;
		tThreadName.setText(thread.userScreenName);
		tThreadText.setText(thread.text);
		vThread.setVisibility(View.VISIBLE);
		vThread.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Utils.goStatusPage(mContext, thread);
			}
		});

	}

	private void doFetchThread() {
		ResultReceiver receiver = new ResultReceiver(mHandler) {

			@Override
			protected void onReceiveResult(int resultCode, Bundle resultData) {
				switch (resultCode) {
				case Commons.RESULT_CODE_START:
					break;
				case Commons.RESULT_CODE_FINISH:
					Status result = (Status) resultData
							.getSerializable(Commons.EXTRA_STATUS);
					if (result != null) {
						showThread(result);
					}
					break;
				case Commons.RESULT_CODE_ERROR:
					break;
				default:
					break;
				}
			}
		};
		Intent intent = new Intent(this, ActionService.class);
		intent.putExtra(Commons.EXTRA_TYPE, Commons.ACTION_STATUS_SHOW);
		intent.putExtra(Commons.EXTRA_ID, status.inReplyToStatusId);
		intent.putExtra(Commons.EXTRA_RECEIVER, receiver);
		startService(intent);
	}

	@Override
	public void onActionSuccess(int type, String message) {
	}

	@Override
	public void onActionFailed(int type, String message) {
	}

}