"""
Tests for AirLLMBaseModel functionality.
"""
import pytest
from unittest.mock import patch, MagicMock, Mock
import torch


class TestAirLLMBaseModel:
    """Test AirLLMBaseModel class."""
    
    @patch('airllm.airllm_base.AutoConfig')
    @patch('airllm.airllm_base.AutoTokenizer')
    @patch('airllm.airllm_base.init_empty_weights')
    @patch('airllm.airllm_base.AutoModelForCausalLM')
    def test_initialization_with_compression(self, mock_model, mock_weights, mock_tokenizer, mock_config):
        """Test model initialization with compression."""
        from airllm.airllm_base import AirLLMBaseModel
        
        mock_config.from_pretrained.return_value = MagicMock()
        mock_tokenizer.from_pretrained.return_value = MagicMock()
        
        # Mock bitsandbytes as not installed
        with patch('airllm.airllm_base.bitsandbytes_installed', False):
            with pytest.raises(ImportError, match="bitsandbytes not found"):
                AirLLMBaseModel(
                    "test-model",
                    compression="4bit"
                )
    
    @patch('airllm.airllm_base.AutoConfig')
    @patch('airllm.airllm_base.AutoTokenizer')
    @patch('airllm.airllm_base.init_empty_weights')
    @patch('airllm.airllm_base.AutoModelForCausalLM')
    def test_initialization_without_compression(self, mock_model, mock_weights, mock_tokenizer, mock_config):
        """Test model initialization without compression."""
        from airllm.airllm_base import AirLLMBaseModel
        
        mock_config.from_pretrained.return_value = MagicMock()
        mock_tokenizer.from_pretrained.return_value = MagicMock()
        mock_model.from_config.return_value = MagicMock()
        
        with patch('airllm.airllm_base.bitsandbytes_installed', False):
            # Should not raise error when compression is None
            model = AirLLMBaseModel(
                "test-model",
                compression=None
            )
            assert model.compression is None
    
    @patch('airllm.airllm_base.AutoConfig')
    @patch('airllm.airllm_base.AutoTokenizer')
    @patch('airllm.airllm_base.init_empty_weights')
    @patch('airllm.airllm_base.AutoModelForCausalLM')
    def test_profiling_mode(self, mock_model, mock_weights, mock_tokenizer, mock_config):
        """Test profiling mode initialization."""
        from airllm.airllm_base import AirLLMBaseModel
        
        mock_config.from_pretrained.return_value = MagicMock()
        mock_tokenizer.from_pretrained.return_value = MagicMock()
        mock_model.from_config.return_value = MagicMock()
        
        with patch('airllm.airllm_base.bitsandbytes_installed', False):
            model = AirLLMBaseModel(
                "test-model",
                profiling_mode=True
            )
            assert model.profiling_mode is True
            assert model.profiler is not None
    
    @patch('airllm.airllm_base.AutoConfig')
    @patch('airllm.airllm_base.AutoTokenizer')
    @patch('airllm.airllm_base.init_empty_weights')
    @patch('airllm.airllm_base.AutoModelForCausalLM')
    def test_device_configuration(self, mock_model, mock_weights, mock_tokenizer, mock_config):
        """Test device configuration."""
        from airllm.airllm_base import AirLLMBaseModel
        
        mock_config.from_pretrained.return_value = MagicMock()
        mock_tokenizer.from_pretrained.return_value = MagicMock()
        mock_model.from_config.return_value = MagicMock()
        
        with patch('airllm.airllm_base.bitsandbytes_installed', False):
            model = AirLLMBaseModel(
                "test-model",
                device="cuda:1"
            )
            assert model.running_device == "cuda:1"
    
    @patch('airllm.airllm_base.AutoConfig')
    @patch('airllm.airllm_base.AutoTokenizer')
    @patch('airllm.airllm_base.init_empty_weights')
    @patch('airllm.airllm_base.AutoModelForCausalLM')
    def test_max_seq_len_configuration(self, mock_model, mock_weights, mock_tokenizer, mock_config):
        """Test max sequence length configuration."""
        from airllm.airllm_base import AirLLMBaseModel
        
        mock_config.from_pretrained.return_value = MagicMock()
        mock_tokenizer.from_pretrained.return_value = MagicMock()
        mock_model.from_config.return_value = MagicMock()
        
        with patch('airllm.airllm_base.bitsandbytes_installed', False):
            model = AirLLMBaseModel(
                "test-model",
                max_seq_len=1024
            )
            assert model.max_seq_len == 1024
