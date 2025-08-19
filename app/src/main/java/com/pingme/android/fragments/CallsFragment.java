package com.pingme.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pingme.android.adapters.CallsAdapter;
import com.pingme.android.databinding.FragmentCallsBinding;
import com.pingme.android.models.Call;

import java.util.ArrayList;
import java.util.List;

public class CallsFragment extends Fragment {

    private FragmentCallsBinding binding;
    private CallsAdapter callsAdapter;
    private List<Call> callsList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCallsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        loadCallHistory();
    }

    private void setupRecyclerView() {
        callsList = new ArrayList<>();
        callsAdapter = new CallsAdapter(callsList, this::onCallClick, this::onCallAgain);
        binding.recyclerViewCalls.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewCalls.setAdapter(callsAdapter);
    }

    private void loadCallHistory() {
        // TODO: Load call history from Firebase or local database
        // For now, show empty state
        showEmptyState();
    }

    private void showEmptyState() {
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.recyclerViewCalls.setVisibility(View.GONE);
    }

    private void onCallClick(Call call) {
        // TODO: Handle call item click (show call details, etc.)
        Toast.makeText(getContext(), "Call details for " + call.getContactName(), Toast.LENGTH_SHORT).show();
    }

    private void onCallAgain(Call call) {
        // TODO: Initiate new call
        Toast.makeText(getContext(), "Calling " + call.getContactName() + "...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}