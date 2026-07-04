"""
Tests for utility functions.
"""
import pytest
from airllm.utils import NotEnoughSpaceException


class TestUtils:
    """Test utility functions."""
    
    def test_not_enough_space_exception(self):
        """Test NotEnoughSpaceException can be raised."""
        with pytest.raises(NotEnoughSpaceException):
            raise NotEnoughSpaceException("Not enough disk space")
