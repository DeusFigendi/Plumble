package com.morlunk.mumbleclient.app;

import java.util.ArrayList;
import java.util.List;

import net.sf.mumble.MumbleProto.PermissionDenied.DenyType;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.Settings.PlumbleCallMode;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Favourite;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
import com.morlunk.mumbleclient.service.IServiceObserver;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;


/**
 * An interface for the activity that manages the channel selection.
 * @author andrew
 *
 */
interface ChannelProvider {
	public Channel getChannel();
	public List<User> getChannelUsers();
	public void sendChannelMessage(String message);
}

interface TokenDialogFragmentListener {
	public void updateAccessTokens(List<String> tokens);
}


public class ChannelActivity extends ConnectedActivity implements ChannelProvider, TokenDialogFragmentListener, SensorEventListener {

	public static final String JOIN_CHANNEL = "join_channel";
	public static final String SAVED_STATE_VISIBLE_CHANNEL = "visible_channel";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide fragments for each of the
     * sections. We use a {@link android.support.v4.app.FragmentPagerAdapter} derivative, which will
     * keep every loaded fragment in memory. If this becomes too memory intensive, it may be best
     * to switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    // Favourites
    private List<Favourite> favourites;
    private MenuItem searchItem;
    private MenuItem favouritesItem;
    private MenuItem mutedButton;
    private MenuItem deafenedButton;
    
	private Channel visibleChannel;
	private ChannelSpinnerAdapter channelAdapter;

	private ProgressDialog mProgressDialog;
	private Button mTalkButton;
	private CheckBox mTalkToggleBox;
	private View mTalkGradient;
	
	// Fragments
	private ChannelListFragment listFragment;
	private ChannelChatFragment chatFragment;
	
	// Proximity sensor
	private SensorManager sensorManager;
	private Sensor proximitySensor;
	
	private Settings settings;
	
	public final DialogInterface.OnClickListener onDisconnectConfirm = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					mService.disconnect();
				}
			}).start();
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
		settings = new Settings(this);
		
		// Use theme from settings
		int theme = 0;
		if(settings.getTheme().equals(Settings.ARRAY_THEME_LIGHTDARK)) {
			theme = R.style.Theme_Sherlock_Light_DarkActionBar;
		} else if(settings.getTheme().equals(Settings.ARRAY_THEME_DARK)) {
			theme = R.style.Theme_Sherlock;
		}
		setTheme(theme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        
        // Handle differences in CallMode
        
        PlumbleCallMode callMode = settings.getCallMode();
        
        if(callMode == PlumbleCallMode.SPEAKERPHONE) {
    		setVolumeControlStream(AudioManager.STREAM_MUSIC);
        } else if(callMode == PlumbleCallMode.VOICE_CALL) {
        	setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        	
        	// Set up proximity sensor
        	sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        	proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowHomeEnabled(false);
        
        // Set up PTT button.
        if(settings.isPushToTalk()) {
        	RelativeLayout pushView = (RelativeLayout) findViewById(R.id.pushview);
        	pushView.setVisibility(View.VISIBLE);
        	
        	mTalkButton = (Button) findViewById(R.id.pushtotalk);
        	mTalkToggleBox = (CheckBox) findViewById(R.id.pushtotalk_toggle);
        	mTalkGradient = findViewById(R.id.pushgradient);
        	
        	mTalkToggleBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(!isChecked) {
						setPushToTalk(false);
					}
				}
			});
        	
        	mTalkButton.setOnTouchListener(new OnTouchListener() {
				
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if(mService == null) {
						return false;
					}
					
					if(!mTalkToggleBox.isChecked()) {
						if(event.getAction() == MotionEvent.ACTION_DOWN)
							setPushToTalk(true);
						else if(event.getAction() == MotionEvent.ACTION_UP)
							setPushToTalk(false);
					} else {
						if(event.getAction() == MotionEvent.ACTION_UP) 
							setPushToTalk(!mService.isRecording());
					}
					
					return true;
				}
			});
        }
        
        if(savedInstanceState != null) {
        	final Channel channel = (Channel) savedInstanceState.getParcelable(SAVED_STATE_VISIBLE_CHANNEL);

			// Channel might be null if we for example caused screen rotation
			// while still connecting.
			if (channel != null) {
				this.visibleChannel = channel;
			}
			
        }
        
        mViewPager = (ViewPager) findViewById(R.id.pager);
        
        // If view pager is present, configure phone UI.
        if(mViewPager != null) {
            // Create the adapter that will return a fragment for each of the three primary sections
            // of the app.
            mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
            // Set up the ViewPager with the sections adapter.
            mViewPager.setAdapter(mSectionsPagerAdapter);
        	
            if(savedInstanceState != null &&
            		savedInstanceState.containsKey(ChannelListFragment.class.getName()) &&
					savedInstanceState.containsKey(ChannelChatFragment.class.getName())) {
				// Load existing fragments
				listFragment = (ChannelListFragment) getSupportFragmentManager().getFragment(savedInstanceState, ChannelListFragment.class.getName());
				chatFragment = (ChannelChatFragment) getSupportFragmentManager().getFragment(savedInstanceState, ChannelChatFragment.class.getName());
			} else {
		        // Create fragments
		        listFragment = new ChannelListFragment();
		        chatFragment = new ChannelChatFragment();
			}
        } else {
        	// Otherwise, create tablet UI.
	        listFragment = (ChannelListFragment) getSupportFragmentManager().findFragmentById(R.id.list_fragment);
	        chatFragment = (ChannelChatFragment) getSupportFragmentManager().findFragmentById(R.id.chat_fragment);
        }

        /*
         * Removed tab code as you are unable to have both tabs and list navigation modes. Use pager only for now.
        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by the adapter.
            // Also specify this Activity object, which implements the TabListener interface, as the
            // listener for when this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
        // When swiping between different sections, select the corresponding tab.
        // We can also use ActionBar.Tab#select() to do this if we have a reference to the
        // Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });
        
        */
    }
    
    public void setPushToTalk(final boolean talking) {
    	if(mService.isRecording() == talking)
    		return;
    	
    	mService.setRecording(talking);
    	
		Animation fade = AnimationUtils.loadAnimation(ChannelActivity.this, talking ? R.anim.fade_in : R.anim.fade_out);
		fade.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
				if(talking)
					mTalkGradient.setVisibility(View.VISIBLE);
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				if(!talking)
					mTalkGradient.setVisibility(View.INVISIBLE);
			}
		});
		mTalkGradient.startAnimation(fade);
    	
    }
    
    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	getSupportFragmentManager().putFragment(outState, ChannelListFragment.class.getName(), listFragment);
    	getSupportFragmentManager().putFragment(outState, ChannelChatFragment.class.getName(), chatFragment);
		outState.putParcelable(SAVED_STATE_VISIBLE_CHANNEL, visibleChannel);
    }
    
    /* (non-Javadoc)
     * @see com.morlunk.mumbleclient.app.ConnectedActivity#onResume()
     */
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if(settings.getCallMode() == PlumbleCallMode.VOICE_CALL)
    		sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
    	
        if(mService != null && mService.getCurrentUser() != null)
        	updateMuteDeafenMenuItems(mService.isMuted(), mService.isDeafened());
        
        // Clear chat notifications when activity is re-opened
        if(mService != null && settings.isChatNotifyEnabled()) {
        	mService.setActivityVisible(true);
        	mService.clearChatNotification();
        }
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	
    	if(settings.getCallMode() == PlumbleCallMode.VOICE_CALL)
    		sensorManager.unregisterListener(this);
    	
    	if(mService != null)
        	mService.setActivityVisible(false);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB) 
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_channel, menu);
        
        searchItem = menu.findItem(R.id.menu_search);
        
        if(VERSION.SDK_INT >= 11) {
        	// Only Honeycomb+ supports the notion of 'searchable info' and the SearchView. We should add search support for eclair+ in a later release. FIXME
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        } else {
        	searchItem.setVisible(false);
        }
        
        favouritesItem = menu.findItem(R.id.menu_favorite_button);
        mutedButton = menu.findItem(R.id.menu_mute_button);
        deafenedButton = menu.findItem(R.id.menu_deafen_button);
        
        if(mService != null &&
        		mService.getCurrentUser() != null) {
        	updateMuteDeafenMenuItems(mService.isMuted(), mService.isDeafened());
        }
        	
        
        return true;
    }
    
	@Override
    protected void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
    	
		// Join channel selected in search suggestions if present
		if(intent != null &&
				intent.getAction() != null &&
				intent.getAction().equals(Intent.ACTION_SEARCH)) {
			Uri data = intent.getData();
			int channelId = Integer.parseInt(data.getLastPathSegment());
			
			new AsyncTask<Integer, Void, Void>() {
				@Override
				protected Void doInBackground(Integer... params) {
					mService.joinChannel(params[0]);
					return null;
				}
			}.execute(channelId);
			
            if(searchItem != null)
            	searchItem.collapseActionView();
		}
    }
    
    
    /**
     * Updates the icon and title of the 'favourites' menu icon to represent the channel's favourited status.
     */
    public void updateFavouriteMenuItem() {
    	if(favouritesItem == null)
    		return;
    	
    	int currentChannel = getChannel().id;
    	
    	boolean isFavouriteChannel = false;
    	for(Favourite favourite : favourites) {
    		if(favourite.getChannelId() == currentChannel) {
    			isFavouriteChannel = true;
    			break;
    		}
    	}
    	
    	favouritesItem.setTitle(isFavouriteChannel ? R.string.removeFavorite : R.string.addFavorite);
    	favouritesItem.setIcon(isFavouriteChannel ? R.drawable.ic_action_favorite_on : R.drawable.ic_action_favorite_off);
    }
    
    /**
     * Updates the 'muted' and 'deafened' action bar icons to reflect the audio status.
     */
    public void updateMuteDeafenMenuItems(boolean muted, boolean deafened) {
    	if(mutedButton == null || deafenedButton == null)
    		return;

    	mutedButton.setIcon(!muted ? R.drawable.microphone : R.drawable.ic_action_microphone_muted);
    	deafenedButton.setIcon(!deafened ? R.drawable.ic_headphones : R.drawable.ic_action_headphones_deafened);
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	switch (item.getItemId()) {
		case R.id.menu_mute_button:
			if(!mService.isMuted()) {
				// Switching to muted
				updateMuteDeafenMenuItems(true, mService.isDeafened());
			} else {
				// Switching to unmuted
				updateMuteDeafenMenuItems(false, false);
			}
			mService.setMuted(!mService.isMuted());
			return true;
		case R.id.menu_deafen_button:
			updateMuteDeafenMenuItems(!mService.isDeafened(), !mService.isDeafened());
			mService.setDeafened(!mService.isDeafened());
			return true;
		case R.id.menu_favorite_button:
			toggleFavourite(getChannel());
			return true;
		case R.id.menu_view_favorites_button:
			showFavouritesDialog();
			return true;
		case R.id.menu_search:
			return false;
		case R.id.menu_access_tokens_button:
			TokenDialogFragment dialogFragment = TokenDialogFragment.newInstance();
			//if(mViewPager != null) {
				// Phone
				//getSupportFragmentManager().beginTransaction().replace(R.id.pager, dialogFragment).commit();
			//} else {
				// Tablet
				dialogFragment.show(getSupportFragmentManager(), "tokens");
			//}
			return true;
		case R.id.menu_disconnect_item:
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					mService.disconnect();
					
				}
			}).start();
			return true;
		}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			final AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle(R.string.disconnect);
			b.setMessage(R.string.disconnectSure);
			b.setPositiveButton(android.R.string.yes, onDisconnectConfirm);
			b.setNegativeButton(android.R.string.no, null);
			b.show();

			return true;
		}
		
		// Push to talk hardware key
		if(settings.isPushToTalk() && 
				keyCode == settings.getPushToTalkKey() && 
				event.getAction() == KeyEvent.ACTION_DOWN) {
			setPushToTalk(true);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Push to talk hardware key
    	if(settings.isPushToTalk() && 
				keyCode == settings.getPushToTalkKey() && 
				event.getAction() == KeyEvent.ACTION_UP) {
			setPushToTalk(false);
			return true;
		}
    	
    	return super.onKeyUp(keyCode, event);
    }
    
    /**
	 * Handles activity initialization when the Service has connected.
	 *
	 * Should be called when there is a reason to believe that the connection
	 * might have became valid. The connection MUST be established but other
	 * validity criteria may still be unfilled such as server synchronization
	 * being complete.
	 *
	 * The method implements the logic required for making sure that the
	 * Connected service is in such a state that it fills all the connection
	 * criteria for ChannelList.
	 *
	 * The method also takes care of making sure that its initialization code
	 * is executed only once so calling it several times doesn't cause problems.
	 */
    
	@Override
	protected void onConnected() {
		// We are now connected! \o/
		
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
		
		// Send access tokens after connection.
		sendAccessTokens();
		
        // Load favourites
        favourites = loadFavourites();
        
		List<Channel> channelList = mService.getSortedChannelList();
		channelAdapter = new ChannelSpinnerAdapter(channelList);
		getSupportActionBar().setListNavigationCallbacks(channelAdapter, new OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(final int itemPosition, long itemId) {
				
				new AsyncTask<Channel, Void, Void>() {
					
					@Override
					protected Void doInBackground(Channel... params) {
						if(visibleChannel == null || !visibleChannel.equals(params[0]))
							mService.joinChannel(params[0].id);
						
						return null;
					}
					
				}.execute(channelAdapter.getItem(itemPosition));
				return true;
			}
		});
		
		// If we don't have visible channel selected, get the last stored channel from preferences.
		// Setting channel also synchronizes the UI so we don't need to do it manually.
		if (visibleChannel == null) {
			int lastChannelId = settings.getLastChannel(mService.getServerId());
			
			Channel lastChannel = findChannelById(lastChannelId);
			
			if(lastChannel != null) {
				new AsyncTask<Channel, Void, Void>() {
					
					@Override
					protected Void doInBackground(Channel... params) {
						mService.joinChannel(params[0].id);
						return null;
					}
					
				}.execute(lastChannel);
			} else {
				setVisibleChannel(mService.getCurrentChannel());
			}
		} else {
			// Re-select visible channel. Necessary after a rotation is
			// performed or the app is suspended.
			if (channelList.contains(visibleChannel)) {
				setVisibleChannel(visibleChannel);
			}
		}
		
		final List<Message> messages = mService.getMessageList();
		for (final Message m : messages) {
			chatFragment.addMessage(m);
		}
		
		// Start recording for voice activity, as there is no push to talk button.
		if(settings.isVoiceActivity()) {
			mService.setRecording(true);
		}
	}

	/**
	 * Retrieves and sends the access tokens for the active server from the database.
	 */
	public void sendAccessTokens() {
		DbAdapter dbAdapter = new DbAdapter(this);
		AsyncTask<DbAdapter, Void, Void> accessTask = new AsyncTask<DbAdapter, Void, Void>() {

			@Override
			protected Void doInBackground(DbAdapter... params) {
				DbAdapter adapter = params[0];
				adapter.open();
				List<String> tokens = adapter.fetchAllTokens(mService.getServerId());
				adapter.close();
				mService.sendAccessTokens(tokens);
				return null;
			}
		};
		accessTask.execute(dbAdapter);
	}
	
	/**
	 * Sends the passed access tokens to the server.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void updateAccessTokens(List<String> tokens) {
		AsyncTask<List<String>, Void, Void> accessTask = new AsyncTask<List<String>, Void, Void>() {

			@Override
			protected Void doInBackground(List<String>... params) {
				List<String> tokens = params[0];
				mService.sendAccessTokens(tokens);
				return null;
			}
		};
		accessTask.execute(tokens);
	}

	/**
	 * Handles activity initialization when the Service is connecting.
	 */
	@Override
	protected void onConnecting() {
		showProgressDialog(R.string.connectionProgressConnectingMessage);
	}

	@Override
	protected void onSynchronizing() {
		showProgressDialog(R.string.connectionProgressSynchronizingMessage);
	}
	
	/* (non-Javadoc)
	 * @see com.morlunk.mumbleclient.app.ConnectedActivity#createServiceObserver()
	 */
	@Override
	protected IServiceObserver createServiceObserver() {
		return new ChannelServiceObserver();
	}
	
	private void showProgressDialog(final int message) {
		if (mProgressDialog == null) {
			mProgressDialog = ProgressDialog.show(
				ChannelActivity.this,
				getString(R.string.connectionProgressTitle),
				getString(message),
				true,
				true,
				new OnCancelListener() {
					@Override
					public void onCancel(final DialogInterface dialog) {
						mProgressDialog.setMessage(getString(R.string.connectionProgressDisconnectingMessage));
						new Thread(new Runnable() {
							
							@Override
							public void run() {
								mService.disconnect();								
							}
						}).start();
					}
				});
		} else {
			mProgressDialog.setMessage(getString(message));
		}
	}
	
	private void toggleFavourite(final Channel channel) {
		
		Favourite currentFavourite = null;
		
		for(Favourite favourite : favourites) {
			if(favourite.getChannelId() == channel.id) {
				currentFavourite = favourite;
			}
		}
		
		final Favourite channelFavourite = currentFavourite;
		
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				DbAdapter dbAdapter = new DbAdapter(ChannelActivity.this);
				dbAdapter.open();

				if (channelFavourite == null)
					dbAdapter.createFavourite(mService.getServerId(),
							channel.id);
				else
					dbAdapter.deleteFavourite(channelFavourite.getId());

				dbAdapter.close();
				return null;
			}

			protected void onPostExecute(Void result) {
				Toast.makeText(
						ChannelActivity.this,
						channelFavourite == null ? R.string.favoriteAdded
								: R.string.favoriteRemoved, Toast.LENGTH_SHORT)
						.show();

				favourites = loadFavourites();
				updateFavouriteMenuItem();
			};
		}.execute();
	}
	
	public List<Favourite> loadFavourites() {
        DbAdapter dbAdapter = new DbAdapter(this);
        dbAdapter.open();
        List<Favourite> favouriteResult = dbAdapter.fetchAllFavourites(mService.getServerId());
        dbAdapter.close();
        return favouriteResult;
	}
	
	private void showFavouritesDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.favorites);
		
		List<CharSequence> items = new ArrayList<CharSequence>();
		final List<Favourite> activeFavourites = new ArrayList<Favourite>(favourites);
		
		for(Favourite favourite : favourites) {
			int channelId = favourite.getChannelId();
			Channel channel = findChannelById(channelId);
			
			if(channel != null) {
				items.add(channel.name);
			} else {
				// TODO remove the favourite from DB here if channel is not found.
				activeFavourites.remove(favourite);
			}
		}
		
		if(items.size() > 0) {
			builder.setItems(items.toArray(new CharSequence[items.size()]), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Favourite favourite = activeFavourites.get(which);
					final Channel channel = findChannelById(favourite.getChannelId());
					
					new AsyncTask<Channel, Void, Void>() {
						
						@Override
						protected Void doInBackground(Channel... params) {
							mService.joinChannel(params[0].id);
							return null;
						}
					}.execute(channel);
				}
			});
		} else {
			builder.setMessage(R.string.noFavorites);
		}
		
		builder.setNegativeButton(android.R.string.cancel, null);
		
		builder.show();
	}
	
	public void setVisibleChannel(Channel channel) {		
		this.visibleChannel = channel;
		listFragment.updateChannel();
		
		// Update action bar
		getSupportActionBar().setSelectedNavigationItem(channelAdapter.availableChannels.indexOf(channel));
        
        // Update favourites icon
		updateFavouriteMenuItem();
		
		// Update last channel in settings
		int channelId = channel.id;
		if(settings.getLastChannel(mService.getServerId()) != channelId) {
			settings.setLastChannel(mService.getServerId(), channel.id); // Cache the last channel
		}
	}
	
	/* (non-Javadoc)
	 * @see com.morlunk.mumbleclient.app.ChannelProvider#getChannel()
	 */
	@Override
	public Channel getChannel() {
		return visibleChannel;
	}
	
	/**
	 * Looks through the list of channels and returns a channel with the passed ID. Returns null if not found.
	 */
	public Channel findChannelById(int channelId) {
		List<Channel> channels = mService.getChannelList();
		for(Channel channel : channels) {
			if(channel.id == channelId) {
				return channel;
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.morlunk.mumbleclient.app.ChannelProvider#getChannelUsers()
	 */
	@Override
	public List<User> getChannelUsers() {
		return mService.getUserList();
	}
	
	/* (non-Javadoc)
	 * @see com.morlunk.mumbleclient.app.ChannelProvider#sendChannelMessage(java.lang.String)
	 */
	@Override
	public void sendChannelMessage(String message) {
		mService.sendChannelTextMessage(
				message,
				visibleChannel);
	}
	
	/**
	 * @param reason 
	 * @param valueOf
	 */
	private void permissionDenied(String reason, DenyType denyType) {
		Toast.makeText(getApplicationContext(), R.string.permDenied, Toast.LENGTH_SHORT).show();
	}
	
	// Voice call mode sensors
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor == proximitySensor) {
			float distance = event.values[0];
			setVisible(event.sensor.getMaximumRange() == distance);
		}
	}
	
    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the primary
     * sections of the app.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
    	
        public SectionsPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public SherlockFragment getItem(int i) {
        	switch (i) {
			case 0:
				return listFragment;
			case 1:
				return chatFragment;
			default:
				return null;
			}
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return getString(R.string.title_section1).toUpperCase();
                case 1: return getString(R.string.title_section2).toUpperCase();
            }
            return null;
        }
    }

    class ChannelServiceObserver extends BaseServiceObserver {
		@Override
		public void onMessageReceived(final Message msg) throws RemoteException {
			chatFragment.addMessage(msg);
		}

		@Override
		public void onMessageSent(final Message msg) throws RemoteException {
			chatFragment.addMessage(msg);
		}
		
		@Override
		public void onCurrentChannelChanged() throws RemoteException {
			Channel userChannel = mService.getCurrentChannel();
			if(userChannel != visibleChannel) {
				setVisibleChannel(userChannel);
			}
		}

		@Override
		public void onCurrentUserUpdated() throws RemoteException {
			updateMuteDeafenMenuItems(mService.getCurrentUser().muted, mService.getCurrentUser().deafened);
		}

		@Override
		public void onUserAdded(final User user) throws RemoteException {
			refreshUser(user);
		}

		@Override
		public void onUserRemoved(final User user, String reason) throws RemoteException {
			if(user.equals(mService.getCurrentUser())) {
				Log.i(Globals.LOG_TAG, String.format("Kicked: \"%s\"", reason));
				mService.setError(getString(R.string.kickedMessage, reason));
			}
			listFragment.removeUser(user);
		}

		@Override
		public void onUserUpdated(final User user) throws RemoteException {
			refreshUser(user);
		}
		
		/* (non-Javadoc)
		 * @see com.morlunk.mumbleclient.service.BaseServiceObserver#onPermissionDenied(int)
		 */
		@Override
		public void onPermissionDenied(String reason, int denyType) throws RemoteException {
			permissionDenied(reason, DenyType.valueOf(denyType));
		}

		private void refreshUser(final User user) {
			listFragment.updateUser(user);
		}
	};
    
class ChannelSpinnerAdapter implements SpinnerAdapter {
		
		List<Channel> availableChannels;
		
		public ChannelSpinnerAdapter(List<Channel> availableChannels) {
			this.availableChannels = availableChannels;
		}
		
		/* (non-Javadoc)
		 * @see android.widget.Adapter#getCount()
		 */
		@Override
		public int getCount() {
			return availableChannels.size();
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#getItem(int)
		 */
		@Override
		public Channel getItem(int arg0) {
			return availableChannels.get(arg0);
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#getItemId(int)
		 */
		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#getItemViewType(int)
		 */
		@Override
		public int getItemViewType(int arg0) {
			return 0;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
		 */
		@Override
		public View getView(int arg0, View arg1, ViewGroup arg2) {
			View view = arg1;
			if(arg1 == null) {
				view = getLayoutInflater().inflate(R.layout.sherlock_spinner_dropdown_item, arg2, false);
			}
			
			Channel channel = getItem(arg0);
			
			TextView spinnerTitle = (TextView) view.findViewById(android.R.id.text1);
			spinnerTitle.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
			spinnerTitle.setText(channel.name);
			
			return view;
		}
		
		public int getNestedLevel(Channel channel) {
			if(channel.parent != 0) {
				for(Channel c : availableChannels) {
					if(c.id == channel.parent) {
						return 1+getNestedLevel(c);
					}
				}
			}
			return 0;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#getViewTypeCount()
		 */
		@Override
		public int getViewTypeCount() {
			return 1;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#hasStableIds()
		 */
		@Override
		public boolean hasStableIds() {
			return false;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#isEmpty()
		 */
		@Override
		public boolean isEmpty() {
			return false;
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
		 */
		@Override
		public void registerDataSetObserver(DataSetObserver arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
		 */
		@Override
		public void unregisterDataSetObserver(DataSetObserver arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see android.widget.SpinnerAdapter#getDropDownView(int, android.view.View, android.view.ViewGroup)
		 */
		@Override
		public View getDropDownView(int position, View convertView,
				ViewGroup parent) {
			View view = convertView;

			Channel channel = getItem(position);
			
			DisplayMetrics metrics = getResources().getDisplayMetrics();

			// Use rowHeight provided by settings, convert to dp.
			int rowHeight = settings.getChannelListRowHeight();
			int rowHeightDp = (int)(rowHeight * metrics.density + 0.5f);

			if (convertView == null) {
				view = getLayoutInflater().inflate(R.layout.nested_dropdown_item, parent, false);

				// set the height to layout, image and textview
				LayoutParams v_params = view
						.getLayoutParams();
				v_params.height = rowHeightDp;
				view.setLayoutParams(v_params);
			}

			ImageView returnImage = (ImageView) view.findViewById(R.id.return_image);

			// Show 'return' arrow and pad the view depending on channel's
			// nested level.
			// Width of return arrow is 50dp, convert that to px.
			if (channel.parent != -1) {
				returnImage.setVisibility(View.VISIBLE);
				view.setPadding((int) (getNestedLevel(channel) * TypedValue
						.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25,
								metrics)), 0, (int) TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP, 15, metrics), 0);
			} else {
				returnImage.setVisibility(View.GONE);
				view.setPadding((int) TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP, 15, metrics), 0,
						(int) TypedValue.applyDimension(
								TypedValue.COMPLEX_UNIT_DIP, 15, metrics), 0);
			}

			TextView spinnerTitle = (TextView) view.findViewById(R.id.channel_name);
			spinnerTitle.setText(channel.name);

			// colorize root elements, channels with users, channels with many
			// users
			if (settings.getChannellistColorized()) {
				if (channel.parent == 0) {
					spinnerTitle.setTextColor(Color.YELLOW);
				} else if (channel.userCount > 0
						&& channel.userCount < settings.getColorizeThreshold()) {
					spinnerTitle.setTextColor(Color.CYAN);
				} else if (channel.userCount >= settings.getColorizeThreshold()) {
					spinnerTitle.setTextColor(Color.RED);
				} else {
					spinnerTitle.setTextColor(Color.WHITE);
				}
			}
			
			TextView spinnerCount = (TextView) view.findViewById(R.id.channel_count);
			spinnerCount.setText("(" + channel.userCount + ")");
			spinnerCount.setTextColor(getResources().getColor(
			channel.userCount > 0 ? R.color.abs__holo_blue_light : R.color.abs__primary_text_holo_dark));

			return view;
		}
		
	}
}
