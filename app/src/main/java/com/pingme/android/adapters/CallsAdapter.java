package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Call;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class CallsAdapter extends RecyclerView.Adapter<CallsAdapter.CallViewHolder> {
    private final List<Call> calls;
    private final OnCallClickListener callClickListener;
    private final OnCallAgainListener callAgainListener;

    public interface OnCallClickListener {
        void onCallClick(Call call);
    }

    public interface OnCallAgainListener {
        void onCallAgain(Call call);
    }

    public CallsAdapter(List<Call> calls, OnCallClickListener callClickListener, OnCallAgainListener callAgainListener) {
        this.calls = calls;
        this.callClickListener = callClickListener;
        this.callAgainListener = callAgainListener;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call, parent, false);
        return new CallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        holder.bind(calls.get(position));
    }

    @Override
    public int getItemCount() {
        return calls.size();
    }

    class CallViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvType;
        ImageView ivProfile, ivCallAgain;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvType = itemView.findViewById(R.id.tvType);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            ivCallAgain = itemView.findViewById(R.id.ivCallAgain);
        }

        void bind(Call call) {
            tvName.setText(call.getContactName());
            tvType.setText(call.getCallType());
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(call.getTimestamp()));

            // TODO: Load profile image using Glide if available
            // Glide.with(itemView.getContext())
            //     .load(call.getContactImage())
            //     .circleCrop()
            //     .placeholder(R.drawable.ic_profile)
            //     .into(ivProfile);

            itemView.setOnClickListener(v -> callClickListener.onCallClick(call));
            ivCallAgain.setOnClickListener(v -> callAgainListener.onCallAgain(call));
        }
    }
}