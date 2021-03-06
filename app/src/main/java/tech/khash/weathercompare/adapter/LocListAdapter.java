package tech.khash.weathercompare.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import tech.khash.weathercompare.R;
import tech.khash.weathercompare.model.Loc;

/**
 * Created by Khashayar "Khash" Mortazavi
 * <p>
 * Main adapter class for Loc object to be used with RecyclerView in the MainActivity
 */

public class LocListAdapter extends RecyclerView.Adapter<LocListAdapter.LocViewHolder> {

    //list of data
    private final ArrayList<Loc> locArrayList;
    //inflater used for creating the view
    private LayoutInflater inflater;
    //context
    private Context context;

    //This is our listener implemented as an interface, to be used in the Activity
    private ListItemClickListener itemClickListener;

    //Listener for long clicks
    private ListLongClickListener longClickListener;

    //constants for our button clicks
    public static final int TODAY_BUTTON = 1;
    public static final int FORECAST_BUTTON = 2;

    /**
     * The interface that receives onClick messages.
     * //TODO: testing
     * we pass the index of the list (to find the corresponding Loc object from the list.
     * We also pass a second int for which button was clicked (1 = current, 2 = forecast, -1 = no
     * button, just the main item was clicked
     */
    public interface ListItemClickListener {
        void onListItemClick(int clickedItemIndex, int buttonClick);
    }//ListItemLongClickListener

    public interface ListLongClickListener {
        void onLongClick (int position);
    }//ListLongClickListener

    /**
     * Public constructor
     *
     * @param context        : context of the parent activity
     * @param locArrayList : ArrayList<Loc> containing data
     */
    public LocListAdapter(Context context, ArrayList<Loc> locArrayList,
                            ListItemClickListener clickListener, ListLongClickListener longClickListener) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.locArrayList = locArrayList;
        itemClickListener = clickListener;
        this.longClickListener = longClickListener;
    }//constructor

    //It inflates the item layout, and returns a ViewHolder with the layout and the adapter.
    @NonNull
    @Override
    public LocListAdapter.LocViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        View itemView = inflater.inflate(R.layout.list_item_loc,
                parent, false);
        return new LocViewHolder(itemView, this, context);
    }//onCreateViewHolder

    /**
     * This connects the data to the view holder. This is where it creates each item
     *
     * @param holder   : the custome view holder
     * @param position : index of the item in the list
     */
    @Override
    public void onBindViewHolder(@NonNull LocListAdapter.LocViewHolder holder, int position) {

        //Get the corresponding Fence object
        Loc loc = locArrayList.get(position);
        //check for null fence
        if (loc == null) {
            return;
        }
        //set name
        holder.idTextView.setText(loc.getName());
    }//onBindViewHolder

    @Override
    public int getItemCount() {
        if (locArrayList == null) {
            return 0;
        }
        return locArrayList.size();
    }//getItemCount


    //Inner class for the view holder
    class LocViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        //our views
        final TextView idTextView;
        final Button buttonCurrent, buttonForecast;
        final LocListAdapter locListAdapter;
        private Context context;

        //constructor
        private LocViewHolder(View itemView, LocListAdapter adapter, Context context) {
            super(itemView);
            this.context = context;
            //find view
            idTextView = itemView.findViewById(R.id.list_text_id);

            buttonCurrent = itemView.findViewById(R.id.button_current);
            buttonForecast = itemView.findViewById(R.id.button_forecast);
            buttonForecast.setOnClickListener(this);
            buttonCurrent.setOnClickListener(this);
            //adapter
            this.locListAdapter = adapter;
            //for click listener
            itemView.setOnClickListener(this);
            //long click listener
            itemView.setOnLongClickListener(this);
        }//FenceViewHolder

        @Override
        public void onClick(View v) {
            int id = v.getId();
            //get the index of the item
            int position = getLayoutPosition();
            //we capture whether the buttons were clicked or not
            if (id == R.id.button_current) {
                itemClickListener.onListItemClick(position, TODAY_BUTTON);
            } else if (id == R.id.button_forecast) {
                itemClickListener.onListItemClick(position, FORECAST_BUTTON);
            } else {
                itemClickListener.onListItemClick(position, -1);
            }
        }//onClick

        @Override
        public boolean onLongClick(View v) {
            //get the position
            int position = getLayoutPosition();
            longClickListener.onLongClick(position);
            //we have consumed it, so we return true
            return true;
        }
    }//LocViewHolder

}//LocListAdapter
