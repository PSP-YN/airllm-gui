"""
Tests for model checksum verification.
"""
import pytest
import hashlib
from pathlib import Path


class TestChecksumVerification:
    """Test checksum verification for downloaded models."""
    
    def test_sha256_hash_calculation(self):
        """Test SHA-256 hash calculation."""
        from airllm.utils import calculate_sha256
        
        # Create test data
        test_data = b"test model data"
        test_file = Path("/tmp/test_model.gguf")
        test_file.write_bytes(test_data)
        
        try:
            # Calculate hash
            hash_result = calculate_sha256(test_file)
            
            # Verify against known hash
            known_hash = hashlib.sha256(test_data).hexdigest()
            assert hash_result == known_hash
        finally:
            test_file.unlink(missing_ok=True)
    
    def test_verify_checksum_valid(self):
        """Test checksum verification with valid checksum."""
        from airllm.utils import verify_checksum
        
        test_data = b"test model data"
        test_file = Path("/tmp/test_model.gguf")
        test_file.write_bytes(test_data)
        
        known_hash = hashlib.sha256(test_data).hexdigest()
        
        try:
            result = verify_checksum(test_file, known_hash)
            assert result is True
        finally:
            test_file.unlink(missing_ok=True)
    
    def test_verify_checksum_invalid(self):
        """Test checksum verification with invalid checksum."""
        from airllm.utils import verify_checksum
        
        test_data = b"test model data"
        test_file = Path("/tmp/test_model.gguf")
        test_file.write_bytes(test_data)
        
        invalid_hash = "0" * 64  # Invalid hash
        
        try:
            result = verify_checksum(test_file, invalid_hash)
            assert result is False
        finally:
            test_file.unlink(missing_ok=True)
