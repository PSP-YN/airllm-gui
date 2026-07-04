"""
Tests for model persistence and loading.
"""
import pytest
from unittest.mock import patch, MagicMock
from pathlib import Path


class TestModelPersistence:
    """Test model persistence functionality."""
    
    @patch('airllm.utils.ModelPersister')
    def test_load_layer_calls_persister(self, mock_persister):
        """Test that load_layer uses ModelPersister."""
        from airllm.utils import load_layer
        
        mock_persister_instance = MagicMock()
        mock_persister.get_model_persister.return_value = mock_persister_instance
        mock_persister_instance.load_model.return_value = {"test": "tensor"}
        
        result = load_layer("/test/path", "layer.0")
        
        mock_persister_instance.load_model.assert_called_once_with("layer.0", "/test/path")
        assert result == {"test": "tensor"}
    
    @patch('airllm.utils.shutil.disk_usage')
    def test_check_space_sufficient(self, mock_disk_usage):
        """Test disk space check with sufficient space."""
        from airllm.utils import check_space
        
        mock_disk_usage.return_value = MagicMock(total=1000, used=500, free=500)
        
        # Should not raise exception with sufficient space
        result = check_space(Path("/test"), compression=None)
        assert result is not None
    
    @patch('airllm.utils.shutil.disk_usage')
    def test_check_space_insufficient(self, mock_disk_usage):
        """Test disk space check with insufficient space."""
        from airllm.utils import check_space, NotEnoughSpaceException
        
        mock_disk_usage.return_value = MagicMock(total=1000, used=900, free=100)
        
        # Mock glob to return large files
        with patch('airllm.utils.glob') as mock_glob:
            mock_glob.return_value = ["/test/model.bin"]
            with patch('airllm.utils.os.path.getsize', return_value=1_000_000_000):  # 1GB
                with pytest.raises(NotEnoughSpaceException):
                    check_space(Path("/test"), compression=None)
