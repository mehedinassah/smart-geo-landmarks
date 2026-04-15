package com.smartlandmarks.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartlandmarks.databinding.FragmentActivityBinding
import com.smartlandmarks.viewmodel.LandmarkViewModel

class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LandmarkViewModel by activityViewModels()
    private lateinit var adapter: VisitHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VisitHistoryAdapter()
        binding.rvVisitHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVisitHistory.adapter = adapter

        viewModel.visitHistory.observe(viewLifecycleOwner) { history ->
            adapter.submitList(history)
            binding.tvEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
