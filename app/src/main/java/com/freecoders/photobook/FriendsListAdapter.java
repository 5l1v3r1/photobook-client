package com.freecoders.photobook;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.freecoders.photobook.common.Constants;
import com.freecoders.photobook.common.Photobook;
import com.freecoders.photobook.db.FriendEntry;
import com.freecoders.photobook.network.ServerInterface;
import com.freecoders.photobook.network.VolleySingleton;
import com.freecoders.photobook.utils.MemoryLruCache;
import com.freecoders.photobook.utils.DiskLruBitmapCache;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Alex on 2014-11-28.
 */
public class FriendsListAdapter extends ArrayAdapter<FriendEntry> {
    private static String LOG_TAG = "FriendsListAdapter";
    int resource;
    String response;
    Context context;
    ImageLoader imageLoader;
    ArrayList<FriendEntry> mFriendList;
    //Initialize adapter
    public FriendsListAdapter(Context context, int resource, ArrayList<FriendEntry> items) {
        super(context, resource, items);
        this.resource=resource;
        ImageLoader.ImageCache memoryCache = new MemoryLruCache();
        try {
            DiskLruBitmapCache diskCache = new DiskLruBitmapCache(context, "DiskCache",
                2000000, Bitmap.CompressFormat.JPEG, 100);
            imageLoader = new ImageLoader(VolleySingleton.getInstance(context).getRequestQueue(),
                    diskCache);
        } catch (Exception e) {
            imageLoader = new ImageLoader(VolleySingleton.getInstance(context).getRequestQueue(),
                    memoryCache);
            Log.d(Constants.LOG_TAG, "Failed to initialize disk cache");
        }
        this.mFriendList = items;
    }

    static class ViewHolder {
        TextView nameText;
        CircleImageView imgAvatar;
        Button followButton;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent)
    {
        FriendEntry profile = getItem(position);
        View rowView = convertView;
        ViewHolder holder;

        if(rowView == null) {
            String inflater = Context.LAYOUT_INFLATER_SERVICE;
            LayoutInflater vi = (LayoutInflater)getContext().getSystemService(inflater);
            rowView = vi.inflate(R.layout.row_friend_list, null);
            holder = new ViewHolder();
            holder.nameText = (TextView)rowView.findViewById(R.id.txtName);
            holder.imgAvatar = (CircleImageView)rowView.findViewById(R.id.imgAvatar);
            holder.followButton = (Button)rowView.findViewById(R.id.btnFollow);
            rowView.setTag(holder);
        } else {
            holder = (ViewHolder) rowView.getTag();
        }

        holder.imgAvatar.setImageResource(R.drawable.avatar);
        holder.nameText.setText(profile.getName());
        holder.imgAvatar.setTag(position);

        if ((profile.getAvatar().isEmpty() == false)
                && (URLUtil.isValidUrl(profile.getAvatar()))) {
            Log.d(Constants.LOG_TAG, "Requesting avatar for " + profile.getName() +
                            " " + profile.getAvatar());
            imageLoader.get(profile.getAvatar().toString(),
                    new ImageListener(position, holder.imgAvatar));
        }

        if (profile.getStatus() == FriendEntry.INT_STATUS_FRIEND) {
            holder.followButton.setText(R.string.btn_unfollow_text);
            final String strUserId = profile.getUserId();
            View.OnClickListener onClickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ServerInterface.removeFriendRequest(createFollowChangeResponse(false, position),
                                Photobook.getMainActivity(), new String[]{strUserId});
                    }
            };
            holder.followButton.setOnClickListener(onClickListener);
        } else {
            holder.followButton.setText(R.string.btn_follow_text);
            if (!profile.getUserId().isEmpty()) {
                final int pos = position;
                final String strUserId = profile.getUserId();
                final FriendsListAdapter adapter = this;
                View.OnClickListener onClickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ServerInterface.addFriendRequest(createFollowChangeResponse(true, position),
                                Photobook.getMainActivity(), new String[]{strUserId});
                    }
                };
                holder.followButton.setOnClickListener(onClickListener);
            }
        }

        return rowView;
    }

    private Response.Listener<String> createFollowChangeResponse(final boolean followRequest, final int position) {
        return new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject resJson = new JSONObject(response);
                    String strRes = resJson.getString(Constants.RESPONSE_RESULT);
                    if (strRes.equals(Constants.RESPONSE_RESULT_OK)) {
                        mFriendList.get(position).setStatus(
                                followRequest? FriendEntry.INT_STATUS_FRIEND : FriendEntry.INT_STATUS_DEFAULT);
                        notifyDataSetChanged();
                        Photobook.getFriendsDataSource().updateFriend(mFriendList.get(position));
                    }
                } catch (JSONException e) {
                    Log.d(LOG_TAG, "Exception " + e.getLocalizedMessage());
                }
                Log.d(LOG_TAG, "Response: " + response);
            }
        };
    }

    private class ImageListener implements ImageLoader.ImageListener {
        Integer pos = 0;
        CircleImageView imgAvatar;

        public ImageListener(Integer position, CircleImageView imgAvatar){
            this.pos = position;
            this.imgAvatar = imgAvatar;
        }

        @Override
        public void onErrorResponse(VolleyError error) {
        }

        @Override
        public void onResponse(ImageLoader.ImageContainer response, boolean arg1) {
            if ((response.getBitmap() != null) && ((Integer) imgAvatar.getTag() == pos)) {
                imgAvatar.setImageResource(0);
                imgAvatar.setImageBitmap(response.getBitmap());
            }
        }
    }
}