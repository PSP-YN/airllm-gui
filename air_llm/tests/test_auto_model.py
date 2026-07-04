"""
Tests for AutoModel functionality.
"""
import pytest
from unittest.mock import patch, MagicMock
from airllm import AutoModel


class TestAutoModel:
    """Test AutoModel class."""
    
    @patch('airllm.auto_model.AutoConfig')
    def test_get_module_class_llama(self, mock_config):
        """Test model class detection for Llama."""
        mock_config.from_pretrained.return_value = MagicMock(architectures=["LlamaForCausalLM"])
        
        module, cls = AutoModel.get_module_class("meta-llama/Llama-2-7b-hf")
        
        assert module == "airllm"
        assert cls == "AirLLMLlama2"
    
    @patch('airllm.auto_model.AutoConfig')
    def test_get_module_class_qwen(self, mock_config):
        """Test model class detection for Qwen."""
        mock_config.from_pretrained.return_value = MagicMock(architectures=["QWenForCausalLM"])
        
        module, cls = AutoModel.get_module_class("Qwen/Qwen-7B")
        
        assert module == "airllm"
        assert cls == "AirLLMQWen"
    
    @patch('airllm.auto_model.AutoConfig')
    def test_get_module_class_unknown(self, mock_config):
        """Test model class detection for unknown architecture."""
        mock_config.from_pretrained.return_value = MagicMock(architectures=["UnknownModel"])
        
        module, cls = AutoModel.get_module_class("unknown/model")
        
        # Should default to Llama2
        assert module == "airllm"
        assert cls == "AirLLMLlama2"
    
    def test_init_raises_error(self):
        """Test that direct instantiation raises error."""
        with pytest.raises(EnvironmentError, match="AutoModel is designed to be instantiated"):
            AutoModel()
